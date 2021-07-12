### This is a full chipyard setup

# BUILD BASE FOR CI

FROM ubuntu:18.04 as base
ARG CHIPYARD_HASH

MAINTAINER https://groups.google.com/forum/#!forum/chipyard

# Install dependencies for ubuntu-req.sh
RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
               curl \
               git \
               sudo \
               ca-certificates \
               keyboard-configuration \
               console-setup \
               bc \
               unzip

WORKDIR /root

# Install Chipyard and run ubuntu-req.sh to install necessary dependencies
RUN git clone https://github.com/ucb-bar/chipyard.git && \
        cd chipyard && \
        git checkout $CHIPYARD_HASH && \
        ./scripts/ubuntu-req.sh 1>/dev/null && \
        sudo rm -rf /var/lib/apt/lists/*

# Update PATH for RISCV toolchain (note: hardcoded for CircleCI)
ENV RISCV="/root/riscv-tools-install"
ENV LD_LIBRARY_PATH="$RISCV/lib"
ENV PATH="$RISCV/bin:$PATH"

# BUILD IMAGE WITH TOOLCHAINS

# Use above build as base
FROM base as base-with-tools

# Init submodules
RUN cd chipyard && \
        export MAKEFLAGS=-"j $(nproc)" && \
        ./scripts/init-submodules-no-riscv-tools.sh 1>/dev/null

# Install riscv-tools
RUN cd chipyard && \
        export MAKEFLAGS=-"j $(nproc)" && \
        ./scripts/build-toolchains.sh riscv-tools 1>/dev/null

# Install esp-tools
RUN cd chipyard && \
        export MAKEFLAGS=-"j $(nproc)" && \
        ./scripts/build-toolchains.sh esp-tools 1>/dev/null


# Set up FireMarshal
SHELL ["/bin/bash", "-c"] 
RUN cd chipyard && \
        source env.sh && \
        cd software/firemarshal && \
        ./init-submodules.sh && \
        pip3 install -r python-requirements.txt && \
        marshal build br-base.json 
        

# Run script to set environment variables on entry
ENTRYPOINT ["chipyard/scripts/entrypoint.sh"]

# END IMAGE CUSTOMIZATIONS

CMD ["/bin/sh"]
