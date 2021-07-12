General
-------
This DockerFile contains the necessary steps to build a Docker container that can run
projects with riscv-tools, chisel3, firrtl, and verilator. When run up to the base stage, it installs the necessary
apt-get packages and sets the environment variables needed for CircleCI. When run up to the base-with-tools stage, it initializes and installs the necessary toolchains for running simulations and testing projects.

Build and Deploy the Container
------------------------------

    sudo docker build --target base . # to build the image for the CI
    sudo docker build --target base --build-arg CHIPYARD_HASH=<COMMIT_HASH> . # to build the image for the CI from a specific chipyard commit
    sudo docker build --target base-with-tools . # to build the full image
    sudo docker tag <IMAGE_ID> <PATH_NAME>:tag . # to tag the image after the build (ex. 0.0.3)
    sudo docker login # login into the account to push to
    sudo docker push <PATH_NAME>:tag # to push to repo with tag
    sudo docker run -it --privileged <IMAGE_ID> bash # to run an interactive version of the container


Path Names
----------
Older docker images (when this Dockerfile was in `riscv-boom/riscv-boom`) can be found in the <PATH_NAME> `riscvboom/riscvboom-images`.
Current up-to-date images are located in <PATH_NAME> `ucbbar/chipyard-image`. NOTE: Less recent images in this path may not have toolchains initialized
Current up-to-date CI images are located in <PATH_NAME> `ucbbar/chipyard-ci-image`.
