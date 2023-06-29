FROM ubuntu:latest

RUN apt-get -y update &&  \
    apt-get -y install wget gnupg2 software-properties-common

# Setup correto JDK
RUN wget -O- https://apt.corretto.aws/corretto.key | apt-key add - && \
    add-apt-repository 'deb https://apt.corretto.aws stable main' && \
    apt-get -y update &&  \
    apt-get -y install java-17-amazon-corretto-jdk

# CEF dependencies
RUN apt-get -y install libx11-dev libnss3 libatk1.0-0 libatk-bridge2.0-0  \
    libcups2 libxcomposite-dev libxdamage-dev libxrandr-dev libgbm-dev libxkbcommon-dev \
    libpangocairo-1.0-0 libasound2-dev xvfb

ENV LANG=C.UTF-8
ENV JAVA_HOME=/usr/lib/jvm/java-17-amazon-corretto
# Do not set display variable, so we won't force awt/swing to start headful
# ENV DISPLAY=:0.0

# Run before preview tests
# Xvfb -ac :0 -screen 0 1920x1080x16 &
