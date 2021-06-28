#!/bin/bash

# create the riscv tools/esp tools binaries
# passed in as <riscv-tools or esp-tools>

# turn echo on and error on earliest command
set -ex

# get shared variables
SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"
source $SCRIPT_DIR/defaults.sh

if [ ! -d "$HOME/$1-install" ]; then
    cd $HOME

    # init all submodules including the tools
    CHIPYARD_DIR="$LOCAL_CHIPYARD_DIR" NPROC=$CI_MAKE_NPROC $LOCAL_CHIPYARD_DIR/scripts/build-toolchains.sh $1
fi
