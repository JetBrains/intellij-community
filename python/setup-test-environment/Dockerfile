# registry.jetbrains.team/p/ij/test-containers/pycharm-tests
# https://buildserver.labs.intellij.net/buildConfiguration/ijplatform_master_PyCharmEnvTestsLinuxInDocker_SetupTestEnvironment
ARG BASE_IMAGE=debian:12

FROM $BASE_IMAGE AS builder
LABEL maintainer="Ilya Kazakevich"

# Pythons will go here
ENV PYCHARM_PYTHONS=/pythons/
ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && \
    apt-get install -y curl gcc make build-essential libssl-dev zlib1g-dev libbz2-dev libreadline-dev libsqlite3-dev wget pgp \
                       llvm libncurses5-dev libncursesw5-dev xz-utils tk-dev libffi-dev liblzma-dev python3-pip default-jre-headless && \
    rm -rf /var/lib/apt/lists/*
RUN curl -fsSL https://download.docker.com/linux/debian/gpg | gpg --dearmor -o /docker-archive-keyring.gpg 
# To install docker

ADD . .
RUN ./gradlew build

############################

FROM $BASE_IMAGE AS runner
ENV DEBIAN_FRONTEND=noninteractive
COPY --from=builder /docker-archive-keyring.gpg /usr/share/keyrings/docker-archive-keyring.gpg
RUN echo "deb [arch=amd64 signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/debian bookworm stable" > /etc/apt/sources.list.d/docker.list 

# xterm installs freetype, xlib, cb etc. GL is also required by some python tests
# First, install certs to install docker. Then update, then install docker and other tools
# Docker cli is used in tests
RUN apt-get update && apt-get install -y ca-certificates && apt-get upgrade -y && apt-get update && apt-get install -y openssl libgl1 zsh fish bash xterm locales libsqlite3-dev apt-transport-https ca-certificates curl software-properties-common docker-ce-cli && rm -rf /var/lib/apt/lists/*

# UTF locale is required for testFileEncoding
# libsqlite3 is for Django
RUN echo "en_US.UTF-8 UTF-8" > /etc/locale.gen && \
    locale-gen

ENV LC_ALL=en_US.UTF-8
ENV PYCHARM_PYTHONS=/pythons/
ENV CONDA_PATH=/pythons/conda/bin/conda

# don't ask the user to accept conda TOS
# see PCQA-1152 and https://github.com/anaconda/conda-anaconda-tos?tab=readme-ov-file#cicd-environments
ENV CONDA_PLUGINS_AUTO_ACCEPT_TOS=yes

COPY --from=builder /pythons/ /pythons

# To make sure all pythons are executable
RUN find / -executable  -type f,l -name "python"  -print0  | xargs -0 -I '{}' sh -c "'{}' --version"
