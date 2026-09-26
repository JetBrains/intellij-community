# JVM TI agent that logs loading and preparation stages of class lifecycle
# Set JAVA_HOME to JBR 17 location

# Usage VM Options: -agentpath:/var/tmp/libvmtrace.so=output.log
# Original source: https://github.com/odnoklassniki/jvmti-tools#vmtrace

build-linux:
	g++ -O2 -fPIC -shared -I $(JAVA_HOME)/include -I $(JAVA_HOME)/include/linux -olibvmtrace.so vmtrace.cpp

build-macos:
	g++ -O2 -fPIC -shared -I $(JAVA_HOME)/include -I $(JAVA_HOME)/include/darwin -olibvmtrace.dylib vmtrace.cpp

# Build with C:\Program Files (x86)\Microsoft Visual Studio\2019\BuildTools\VC\Tools\MSVC\14.29.30133\bin\Hostx64\x64\cl.exe
build-win64:
  cl.exe /O2 /LD /I "%JAVA_HOME%/include" -I "%JAVA_HOME%/include/win32" vmtrace.cpp