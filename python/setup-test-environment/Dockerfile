FROM debian:12 AS BUILDER
LABEL maintainer="Andrey Lisin"

# Pythons will go here
ENV PYCHARM_PYTHONS=/pythons/


RUN apt-get update \
 && apt-get install -y curl gcc make build-essential libssl-dev zlib1g-dev libbz2-dev libreadline-dev libsqlite3-dev wget \
                       llvm libncurses5-dev libncursesw5-dev xz-utils tk-dev libffi-dev liblzma-dev python3-pip default-jre-headless

# Install conda to location known by ``suggestCondaPath`` to be found by test runner
RUN curl https://repo.anaconda.com/miniconda/Miniconda3-py310_23.3.1-0-Linux-x86_64.sh > ./conda.sh
RUN bash ./conda.sh -b -p /opt/miniconda3/

ADD . .
RUN sh ./gradlew build

############################

FROM debian:12 AS RUNNER
# xterm installs freetype, xlib, cb etc. GL is also required by some python tests
RUN apt-get update && apt-get install -y openssl libgl1 xterm locales libsqlite3-dev
# UTF locale is required for testFileEncoding
# libsqlite3 is for Django
RUN echo "en_US.UTF-8 UTF-8" > /etc/locale.gen
RUN locale-gen
ENV LC_ALL en_US.UTF-8
ENV PYCHARM_PYTHONS=/pythons/

COPY --from=builder /pythons/ /pythons
COPY --from=builder /opt/miniconda3 /opt/miniconda3

# To make sure all pythons are executable
RUN find / -executable  -type f,l -name "python"  -print0  | xargs -0 -I '{}' sh -c "'{}' --version"