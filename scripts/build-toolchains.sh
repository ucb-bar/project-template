#!/usr/bin/env bash

#this script is based on the firesim build toolchains script

# exit script if any command fails
set -e
set -o pipefail

# If BASH_SOURCE is undefined, we may be running under zsh, in that case
# provide a zsh-compatible alternative
DIR="$(dirname "$(readlink -f "${BASH_SOURCE[0]:-${(%):-%x}}")")"
CHIPYARD_DIR="$(dirname "$DIR")"

usage() {
    echo "usage: ${0} [OPTIONS] [riscv-tools | esp-tools | ec2fast]"
    echo ""
    echo "Installation Types"
    echo "   riscv-tools: if set, builds the riscv toolchain (this is also the default)"
    echo "   esp-tools: if set, builds esp-tools toolchain used for the hwacha vector accelerator"
    echo "   ec2fast: if set, pulls in a pre-compiled RISC-V toolchain for an EC2 manager instance"
    echo ""
    echo "Options"
    echo "   --prefix PREFIX       : Install destination. If unset, defaults to $(pwd)/riscv-tools-install"
    echo "                           or $(pwd)/esp-tools-install"
    echo "   --ignore-qemu         : Ignore installing QEMU"
    echo "   --clean-after-install : Run make clean in calls to module_make and module_build"
    echo "   --arch -a             : Architecture (e.g., rv64gc)"
    echo "   --help -h             : Display this message"
    exit "$1"
}

error() {
    echo "${0##*/}: ${1}" >&2
}
die() {
    error "$1"
    exit "${2:--1}"
}

TOOLCHAIN="riscv-tools"
EC2FASTINSTALL="false"
IGNOREQEMU=""
CLEANAFTERINSTALL=""
RISCV=""
ARCH=""

# getopts does not support long options, and is inflexible
while [ "$1" != "" ];
do
    case $1 in
        -h | --help | help )
            usage 3 ;;
        -p | --prefix )
            shift
            RISCV=$(realpath $1) ;;
        --ignore-qemu )
            IGNOREQEMU="true" ;;
        -a | --arch )
            shift
            ARCH=$1 ;;
        --clean-after-install )
            CLEANAFTERINSTALL="true" ;;
        riscv-tools | esp-tools)
            TOOLCHAIN=$1 ;;
        ec2fast )
            EC2FASTINSTALL="true" ;;
        * )
            error "invalid option $1"
            usage 1 ;;
    esac
    shift
done

if [ -z "$RISCV" ] ; then
      INSTALL_DIR="$TOOLCHAIN-install"
      RISCV="$(pwd)/$INSTALL_DIR"
fi

if [ -z "$ARCH" ] ; then
    XLEN=64
elif [[ "$ARCH" =~ ^rv(32|64)((i?m?a?f?d?|g?)c?)$ ]]; then
    XLEN=${BASH_REMATCH[1]}
else
    error "invalid arch $ARCH"
    usage 1
fi

echo "Installing toolchain to $RISCV"

# install risc-v tools
export RISCV="$RISCV"

cd "${CHIPYARD_DIR}"

SRCDIR="$(pwd)/toolchains/${TOOLCHAIN}"
[ -d "${SRCDIR}" ] || die "unsupported toolchain: ${TOOLCHAIN}"
. ./scripts/build-util.sh


if [ "${EC2FASTINSTALL}" = true ] ; then
    [ "${TOOLCHAIN}" = 'riscv-tools' ] ||
        die "unsupported precompiled toolchain: ${TOOLCHAIN}"

    echo '=>  Fetching pre-built toolchain'
    module=toolchains/riscv-tools/riscv-gnu-toolchain-prebuilt
    git config --unset submodule."${module}".update || :
    git submodule update --init --depth 1 "${module}"

    echo '==>  Verifying toolchain version hash'
    # Find commit hash without initializing the submodule
    hashsrc="$(git ls-tree -d HEAD "${SRCDIR}/riscv-gnu-toolchain" | {
        unset IFS && read -r _ type obj _ &&
        test -n "${obj}" && test "${type}" = 'commit' && echo "${obj}"
    }; )" ||
        die 'failed to obtain riscv-gnu-toolchain submodule hash' "$?"

    read -r hashbin < "${module}/HASH" ||
        die 'failed to obtain riscv-gnu-toolchain-prebuilt hash' "$?"

    echo "==>  ${hashsrc}"
    [ "${hashsrc}" = "${hashbin}" ] ||
        die "pre-built version mismatch: ${hashbin}"

    echo '==>  Installing pre-built toolchain'
    "${MAKE}" -C "${module}" DESTDIR="${RISCV}" install
    git submodule deinit "${module}" || :

else
    MAKE_VER=$("${MAKE}" --version) || true
    case ${MAKE_VER} in
        'GNU Make '[4-9]\.*)
            ;;
        'GNU Make '[1-9][0-9])
            ;;
        *)
            die 'obsolete make version; need GNU make 4.x or later'
            ;;
    esac

    module_prepare riscv-gnu-toolchain qemu
    module_build riscv-gnu-toolchain --prefix="${RISCV}" --with-cmodel=medany ${ARCH:+--with-arch=${ARCH}}
    echo '==>  Building GNU/Linux toolchain'
    module_make riscv-gnu-toolchain linux
fi

module_all riscv-isa-sim --prefix="${RISCV}"
# build static libfesvr library for linking into firesim driver (or others)
echo '==>  Installing libfesvr static library'
module_make riscv-isa-sim libfesvr.a
cp -p "${SRCDIR}/riscv-isa-sim/build/libfesvr.a" "${RISCV}/lib/"

CC= CXX= module_all riscv-pk --prefix="${RISCV}" --host=riscv${XLEN}-unknown-elf
module_all riscv-tests --prefix="${RISCV}/riscv${XLEN}-unknown-elf" --with-xlen=${XLEN}

# Common tools (not in any particular toolchain dir)

CC= CXX= SRCDIR="$(pwd)/toolchains" module_all libgloss --prefix="${RISCV}/riscv${XLEN}-unknown-elf" --host=riscv${XLEN}-unknown-elf

if [ -z "$IGNOREQEMU" ] ; then
    echo "=>  Starting qemu build"
    dir="$(pwd)/toolchains/qemu"
    echo "==>   Initializing qemu submodule"
    #since we don't want to use the global config we init passing rewrite config in to the command
    git -c url.https://github.com/qemu.insteadOf=https://git.qemu.org/git submodule update --init --recursive "$dir"
    echo "==>  Applying url-rewriting to avoid git.qemu.org"
    # and once the clones exist, we recurse through them and set the rewrite
    # in the local config so that any further commands by the user have the rewrite. uggh. git, why you so ugly?
    git -C "$dir" config --local url.https://github.com/qemu.insteadOf https://git.qemu.org/git
    git -C "$dir" submodule foreach --recursive 'git config --local url.https://github.com/qemu.insteadOf https://git.qemu.org/git'

    # check to see whether the rewrite rules are needed any more
    # If you find git.qemu.org in any .gitmodules file below qemu, you still need them
    # the /dev/null redirection in the submodule grepping is to quiet non-existance of further .gitmodules
    ! grep -q 'git\.qemu\.org' "$dir/.gitmodules" && \
    git -C "$dir" submodule foreach --quiet --recursive '! grep -q "git\.qemu\.org" .gitmodules 2>/dev/null' && \
    echo "==>  PLEASE REMOVE qemu URL-REWRITING from scripts/build-toolchains.sh. It is no longer needed!" && exit 1

    # now actually do the build
    SRCDIR="$(pwd)/toolchains" module_build qemu --prefix="${RISCV}" --target-list=riscv${XLEN}-softmmu
fi

# make Dromajo
git submodule update --init $CHIPYARD_DIR/tools/dromajo/dromajo-src
make -C $CHIPYARD_DIR/tools/dromajo/dromajo-src/src

rm -f env-$TOOLCHAIN.sh
rm -f env.sh

# create specific env.sh
cat > "$CHIPYARD_DIR/env-$TOOLCHAIN.sh" <<EOF
# auto-generated by build-toolchains.sh
export CHIPYARD_TOOLCHAIN_SOURCED=1
export RISCV=$(printf '%q' "$RISCV")
export PATH=\${RISCV}/bin:\${PATH}
export LD_LIBRARY_PATH=\${RISCV}/lib\${LD_LIBRARY_PATH:+":\${LD_LIBRARY_PATH}"}
EOF

# create general env.sh
echo "# line auto-generated by build-toolchains.sh" >> env.sh
echo "source $(printf '%q' "$CHIPYARD_DIR/env-$TOOLCHAIN.sh")" >> env.sh
echo "Toolchain Build Complete!"
