#!/bin/bash
set -euxo pipefail

g++ -march=armv8-a -shared -o attach_linux_aarch64.so -fPIC -nostartfiles attach.cpp
mv attach_linux_aarch64.so ../attach_linux_aarch64.so
echo Compiled aarch64
