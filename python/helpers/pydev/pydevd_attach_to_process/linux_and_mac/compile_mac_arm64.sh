g++ -fPIC -D_REENTRANT -std=c++11 -arch arm64 -c -o attach_arm64.o attach.cpp
g++ -dynamiclib -nostartfiles -arch arm64 -o attach_arm64.dylib attach_arm64.o -lc
rm attach_arm64.o
mv attach_arm64.dylib ../attach_arm64.dylib
