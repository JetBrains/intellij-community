# JVM TI agent that logs loading and preparation stages of class lifecycle
# Set JAVA_HOME to JBR 17 location

# Usage VM Options: -agentpath:/var/tmp/libvmtrace.so=output.log
# Original source: https://github.com/odnoklassniki/jvmti-tools#vmtrace

build-linux:
	g++ -O2 -fPIC -shared -I $(JAVA_HOME)/include -I $(JAVA_HOME)/include/linux -olibvmtrace.so vmtrace.cpp

build-macos:
	g++ -O2 -fPIC -shared -I $(JAVA_HOME)/include -I $(JAVA_HOME)/include/darwin -olibvmtrace.dylib vmtrace.cpp
