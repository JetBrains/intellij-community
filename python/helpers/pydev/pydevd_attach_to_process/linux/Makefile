# Defaults which can be overridden.
OS = macosx
ARCH_X86 = x86
ARCH_X86_64 = x86_64

CC=g++
LD=libtool
CPPFLAGS = -I.
CFLAGS +=-fPIC -D_REENTRANT -nostartfiles

ARCH_FLAG_X86 = -arch i386
ARCH_FLAG_X86_64 = -arch x86_64

INSTALL_DIR_X86 = ../os/$(OS)/$(ARCH_X86)
INSTALL_DIR_X86_64 = ../os/$(OS)/$(ARCH_X86_64)
INSTALL_DIR_LINUX_X86 = ../os/$(LINUX)/$(ARCH_X86)
INSTALL_DIR_LINUX_X86_64 = ../os/$(LINUX)/$(ARCH_X86_64)

ATTACH = attach_mac.so
ATTACH_NAME_FULL_X86 = $(INSTALL_DIR_X86)/attach_x86.dylib
ATTACH_NAME_FULL_X86_64 = $(INSTALL_DIR_X86_64)/attach_x86_64.dylib

OBJS_ATTACH_X86 = attach_linux_$(ARCH_X86).o
OBJS_ATTACH_X86_64 = attach_linux_$(ARCH_X86_64).o

OBJS_X86 = $(OBJS_ATTACH_X86)
OBJS_X86_64 = $(OBJS_ATTACH_X86_64)

all: x86 x86_64

x86: $(ATTACH_NAME_FULL_X86)

x86_64: $(ATTACH_NAME_FULL_X86_64)

linux_x86: $(ATTACH_NAME_FULL_LINUX_X86)
linux_x86_64: $(ATTACH_NAME_FULL_LINUX_X86_64)

rebuild: clean all

$(ATTACH_NAME_FULL_X86): $(OBJS_ATTACH_X86)
	mkdir -p $(INSTALL_DIR_X86)
	$(CC) -dynamiclib  $(ARCH_FLAG_X86) -o $(ATTACH_NAME_FULL_X86) $(OBJS_ATTACH_X86) -lc

$(ATTACH_NAME_FULL_X86_64): $(OBJS_ATTACH_X86_64)
	mkdir -p $(INSTALL_DIR_X86_64)
	$(CC) -dynamiclib  $(ARCH_FLAG_X86_64) -o $(ATTACH_NAME_FULL_X86_64) $(OBJS_ATTACH_X86_64) -lc

$(ATTACH_NAME_FULL_LINUX_X86): $(OBJS_ATTACH_X86)
	mkdir -p $(INSTALL_DIR_LINUX_X86)
	$(CC) -m32 -g -shared -Wl,-soname,$(ATTACH) $(LDFLAGS) -o $(ATTACH_NAME_FULL_LINUX_X86) $(OBJS_ATTACH_X86)

$(ATTACH_NAME_FULL_LINUX_X86_64): $(OBJS_ATTACH_X86_64)
	mkdir -p $(INSTALL_DIR_LINUX_X86_64)
	$(CC) -g -shared -Wl,-soname,$(ATTACH) $(LDFLAGS) -o $(ATTACH_NAME_FULL_LINUX_X86_64) $(OBJS_ATTACH_X86_64)

attach_linux_$(ARCH_X86).o: attach_linux.c
	$(CC) $(CFLAGS) $(ARCH_FLAG_X86) $(CPPFLAGS) -c -o $@ attach_linux.c

attach_linux_$(ARCH_X86_64).o: attach_linux.c
	$(CC) $(CFLAGS) $(ARCH_FLAG_X86_64) $(CPPFLAGS) -c -o $@ attach_linux.c

clean :
	$(RM) $(OBJS_X86) $(ATTACH_NAME_FULL_X86)
	$(RM) $(OBJS_X86_64) $(ATTACH_NAME_FULL_X86_64)
