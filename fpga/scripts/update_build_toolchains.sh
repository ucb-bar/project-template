#!/bin/bash

#Script to enable multilib support in RISCV-GNU-Toolchain in Chipyard. Useful for using Freedom-E-SDK to compile programs for Arty FPGA boards
#Alexander Lukens 06/21/2021

#Move to script directory
starting_dir=$(pwd)
cd "$(dirname "$0")"

#Path to Chipyard Base DIR
CHIPYARD_DIR=$(pwd)/../..
#Path to 'build-toolchains.sh'
BUILD_TOOLCHAINS=${CHIPYARD_DIR}/scripts/build-toolchains.sh
FREEDOM_SDK_PATH=${1:-$(readlink -f $(pwd)/../../../freedom-e-sdk)}

echo "This script is intended to enable multilib support in the RISCV GNU Toolchain to allow compiling
programs for Arty FPGA board implementations in Chipyard"

if [ $# -eq 0 ];
then
	echo "FREEDOM_SDK_PATH not specified, defaulting to:"
        echo "$FREEDOM_SDK_PATH"
	printf "\nUsage: ./update_build_toolchains.sh <PATH_TO_FREEDOM_SDK>\n\n"
fi

#edit build_toolchians.sh to support multilib
sed -i 's/--enable-multilib//g ' $BUILD_TOOLCHAINS

sed -i 's/module_build riscv-gnu-toolchain --prefix="${RISCV}" --with-cmodel=medany/module_build riscv-gnu-toolchain --prefix="${RISCV}" --with-cmodel=medany --enable-multilib/g' $BUILD_TOOLCHAINS

#if customizations already added, remove them
sed -i -e '/#added by update_build/, +4d' $BUILD_TOOLCHAINS 

#remove pesky empty line after deleted customizations (if it exists)
sed -i '/${LD_LIBRARY_PATH}/{N;s/\n$//}' $BUILD_TOOLCHAINS

#add additional variables to Chipyard ENV files to support Freedom E SDK
sed -i '/${LD_LIBRARY_PATH}"}/ a \\n#added by update_build_toolchains script \nexport RISCV_PATH=${RISCV} \nexport FPGA_PATH=${RISCV}/../fpga' $BUILD_TOOLCHAINS

sed -i "/{RISCV}\/..\/fpga/ a export FREEDOM_SDK=$FREEDOM_SDK_PATH" $BUILD_TOOLCHAINS

 
while true; do
    read -p "Script changes applied, recompile toolchain? (this may take a while) [y/n]" yn
    case $yn in
        [Yy]* ) ${CHIPYARD_DIR}/scripts/build-toolchains.sh; break;;
        [Nn]* ) exit;;
        * ) echo "Please answer yes or no.";;
    esac
done

#return to starting dir
cd $starting_dir
