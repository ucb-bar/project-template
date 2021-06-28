#!/bin/bash

# get the hash of riscv-tools

# turn echo on and error on earliest command
set -ex
set -o pipefail

# get shared variables
SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"
source $SCRIPT_DIR/defaults.sh

# enter bhd repo
#cd $LOCAL_CHIPYARD_DIR

# Use normalized output of git-submodule status as hashfile
ls -ltrd .
whoami
for tools in 'riscv-tools' 'esp-tools' ; do
    touch "${tools}.hash"
done

for tools in 'riscv-tools' 'esp-tools' ; do
    git submodule status "toolchains/${tools}" 'toolchains/libgloss' 'toolchains/qemu' |
    while read -r line ; do
        echo "${line#[!0-9a-f]}"
    done > "${tools}.hash"
done
echo "Hashfile for riscv-tools and esp-tools created in $HOME"
