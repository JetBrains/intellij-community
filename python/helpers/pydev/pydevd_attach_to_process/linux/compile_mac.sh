g++ -fPIC -D_REENTRANT -arch x86_64 I. -c -o attach_linux_x86_64.o attach_linux.c
g++ -dynamiclib -arch x86_64 -o attach_x86_64.dylib attach_linux_x86_64.o -lc


g++ -fPIC -D_REENTRANT -arch i386 -I. -c -o attach_linux_x86.o attach_linux.c
g++ -dynamiclib -arch i386 -o attach_x86.dylib attach_linux_x86.o -lc


