FROM ubuntu:20.04
FROM openjdk:11

LABEL maintainer="Andrey Lisin"

RUN apt-get update \
 && apt-get install -y curl gcc make build-essential libssl-dev zlib1g-dev libbz2-dev libreadline-dev libsqlite3-dev wget \
                       llvm libncurses5-dev libncursesw5-dev xz-utils tk-dev libffi-dev liblzma-dev python3-pip

# Install conda to location known by ``suggestCondaPath`` to be found by test runner
RUN curl https://repo.anaconda.com/miniconda/Miniconda3-py310_23.3.1-0-Linux-x86_64.sh > ./conda.sh
RUN bash ./conda.sh -b -p /opt/miniconda3/