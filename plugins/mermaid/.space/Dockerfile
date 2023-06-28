FROM ubuntu:latest

RUN apt-get -y update && apt-get -y install wget gnupg2 software-properties-common
RUN wget -O- https://apt.corretto.aws/corretto.key | apt-key add -
RUN add-apt-repository 'deb https://apt.corretto.aws stable main'
RUN apt-get -y update
RUN apt-get -y install java-17-amazon-corretto-jdk
RUN apt-get -y install libx11-dev libnss3 libatk1.0-0 libatk-bridge2.0-0  \
    libcups2 libxcomposite-dev libxdamage-dev libxrandr-dev libgbm-dev libxkbcommon-dev \
    libpangocairo-1.0-0 libasound2-dev xvfb

ENV LANG=C.UTF-8
ENV JAVA_HOME=/usr/lib/jvm/java-17-amazon-corretto
ENV DISPLAY=:0.0

# Run before preview tests
# Xvfb -ac :0 -screen 0 1920x1080x16 &
