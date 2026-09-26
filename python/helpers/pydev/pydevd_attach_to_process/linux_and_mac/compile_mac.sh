#!/bin/bash
set -euxo pipefail

clang++ -fPIC -D_REENTRANT -std=c++11 -arch arm64 -c -o attach_arm64.o attach.cpp
clang++ -dynamiclib -nostartfiles -arch arm64 -o attach_arm64.dylib attach_arm64.o -lc
rm attach_arm64.o

clang++ -fPIC -D_REENTRANT -std=c++11 -arch x86_64 -c -o attach_x86_64.o attach.cpp
clang++ -dynamiclib -nostartfiles -arch x86_64 -o attach_x86_64.dylib attach_x86_64.o -lc
rm attach_x86_64.o

lipo -create attach_arm64.dylib attach_x86_64.dylib -output attach.dylib
rm attach_arm64.dylib attach_x86_64.dylib
mv attach.dylib ../attach.dylib
