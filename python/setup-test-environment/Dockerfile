ARG BASE_IMAGE=debian:12

FROM $BASE_IMAGE AS BUILDER
LABEL maintainer="Andrey Lisin"

# Pythons will go here
ENV PYCHARM_PYTHONS=/pythons/

RUN apt-get update && \
    apt-get install -y curl gcc make build-essential libssl-dev zlib1g-dev libbz2-dev libreadline-dev libsqlite3-dev wget \
                       llvm libncurses5-dev libncursesw5-dev xz-utils tk-dev libffi-dev liblzma-dev python3-pip default-jre-headless && \
    rm -rf /var/lib/apt/lists/*

# Install conda to location known by ``suggestCondaPath`` to be found by test runner
ARG MINICONDA_INSTALLER="Miniconda3-py310_23.3.1-0-Linux-x86_64.sh"
ADD --chmod=544 https://repo.anaconda.com/miniconda/$MINICONDA_INSTALLER ./
RUN ./$MINICONDA_INSTALLER -b -p /opt/miniconda3/

ADD . .
RUN ./gradlew build

############################

FROM $BASE_IMAGE AS RUNNER
# xterm installs freetype, xlib, cb etc. GL is also required by some python tests
RUN apt-get update && apt-get install -y openssl libgl1 xterm locales libsqlite3-dev && \
    rm -rf /var/lib/apt/lists/*

# UTF locale is required for testFileEncoding
# libsqlite3 is for Django
RUN echo "en_US.UTF-8 UTF-8" > /etc/locale.gen && \
    locale-gen

ENV LC_ALL en_US.UTF-8
ENV PYCHARM_PYTHONS=/pythons/

COPY --from=builder /pythons/ /pythons
COPY --from=builder /opt/miniconda3 /opt/miniconda3

# To make sure all pythons are executable
RUN find / -executable  -type f,l -name "python"  -print0  | xargs -0 -I '{}' sh -c "'{}' --version"
