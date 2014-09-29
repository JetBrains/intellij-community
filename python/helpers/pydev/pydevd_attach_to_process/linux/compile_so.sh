g++ -m64 -shared -o attach_linux_amd64.so -fPIC -nostartfiles attach_linux.c
mv attach_linux_amd64.so ../attach_linux_amd64.so

echo Note: may need "sudo apt-get install libx32gcc-4.8-dev" and "sudo apt-get install libc6-dev-i386" and "sudo apt-get install g++-multilib" to compile 32 bits

g++ -m32 -shared -o attach_linux_x86.so -fPIC -nostartfiles attach_linux.c
mv attach_linux_x86.so ../attach_linux_x86.so