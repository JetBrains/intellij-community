# Building webp jni library

The webp support requires a native library (`libwebp_jni`) and `libwebp.jar`.
The native libraries are added to the `tools/adt/idea/` project in
`adt-ui/lib/libwebp/[platform]`.

This describes how to build `libwebp_jni` for each of the platforms. The steps
must be repeated for each platform - once for 32 bit and once for 64 bit
architectures. __Make sure to do a clean build when switching architectures__:
`git clean -dfX`.

`libwebp.jar` is copied directly from `<libwebp>/swig/` to `adt-ui/lib/`

## Windows:
1. Install Visual Studio 2015 with the following components:
   - Visual C++ (including all sub components)
   - Universal Windows App Development Tools
   - Git for Windows
1. Open "Git Bash", clone libwebp and checkout appropriate branch:
   ```bat
   git clone https://chromium.googlesource.com/webm/libwebp
   ```
1. Open "Developer Command Prompt for VS2015"
1. Make the webp_jni library as per the following commands:
    ```bat
    # For 64 bit arch (not needed for 32 bit arch)
    cd "C:\Program Files (x86)\Microsoft Visual Studio 14.0\VC"
    vcvarsall amd64
     
    cd \git\clone\location\libwebp
    # Make libwebp
    nmake /f Makefile.vc CFG=release-static RTLIBCFG=static OBJDIR=output
    # Make jni binding now
    cd swig
    # For 32 bit
    cl /I..\src /I"%JDK%\include" ^
    /I"%JDK%\include\win32" ^
    libwebp_java_wrap.c /MT /LD /Fe:webp_jni.dll ^
    ..\output\release-static\x86\lib\libwebp.lib
    # For 64 bit
    cl /I..\src /I"C:\Program Files (x86)\Java\jdk1.7.0_55\include" ^
    /I"C:\Program Files (x86)\Java\jdk1.7.0_55\include\win32" ^
    libwebp_java_wrap.c /MT /LD /Fe:webp_jni64.dll ^
    ..\output\release-static\x64\lib\libwebp.lib
    ```
1. Note that unlike mac and linux, windows library is named `webp_jni` and not
   `libwebp_jni`.

## Mac/Linux
1. Clone libwebp and checkout appropriate branch:
   ```bash
   git clone https://chromium.googlesource.com/webm/libwebp
   ```
1. Edit makefile.unix:
    ```diff
    --- a/makefile.unix
    +++ b/makefile.unix
    @@ -14,10 +14,11 @@
     # These flags assume you have libpng, libjpeg, libtiff and libgif installed. If
     # not, either follow the install instructions below or just comment out the next
     # four lines.
    -EXTRA_FLAGS= -DWEBP_HAVE_PNG -DWEBP_HAVE_JPEG -DWEBP_HAVE_TIFF
    -DWEBP_LIBS= -lpng -lz
    -CWEBP_LIBS= $(DWEBP_LIBS) -ljpeg -ltiff
    -GIF_LIBS = -lgif
    +EXTRA_FLAGS= -fPIC -m32
    +#-DWEBP_HAVE_PNG -DWEBP_HAVE_JPEG -DWEBP_HAVE_TIFF
    +#DWEBP_LIBS= -lpng -lz
    +#CWEBP_LIBS= $(DWEBP_LIBS) -ljpeg -ltiff
    +#GIF_LIBS = -lgif

     ifeq ($(strip $(shell uname)), Darwin)
       # Work around a problem linking tables marked as common symbols,
    ```
1. The above diff is for building for 32 bit arch. To build for 64 bit arch, do
   not add `-m32` to `EXTRA_FLAGS`.
1. Make the libwebp_jni library as per the following commands
   ```bash
   cd /git/clone/location/libwebp
   make -f makefile.unix
   cd swig
   gcc -shared -fPIC -fno-strict-aliasing -O2 \
       -m32 \
       -I/path/to/your/jdk/include \
       -I/path/to/your/jdk/include/[platform] \
       -I../src \
       -L../src \
       libwebp_java_wrap.c \
       -lwebp \
       -o libwebp_jni.[ext]
   ```
1. When building for 64 bit, use `-o libwebp_jni64.[ext]` instead and do not
   specify the `-m32` option.
1. The `[ext]` for linux is `so` and for mac is `dylib`.
1. No need to build the 32 bit variant for mac.
