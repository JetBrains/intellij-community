#!/bin/sh
#
# ------------------------------------------------------
#  PyCharm Startup Script for Unix
# ------------------------------------------------------
#

# ---------------------------------------------------------------------
# Before you run PyCharm specify the location of the
# JDK 1.6 installation directory which will be used for running PyCharm
# ---------------------------------------------------------------------
if [ -z "$PYCHARM_JDK" ]; then
  PYCHARM_JDK=$JDK_HOME
  if [ -z "$PYCHARM_JDK" -a -e "$JAVA_HOME/lib/tools.jar" ]; then
    PYCHARM_JDK=$JAVA_HOME
  fi
  if [ -z "$PYCHARM_JDK" ]; then
    # Try to get the jdk path from java binary path
    JAVA_BIN_PATH=`which java`
    if [ -n "$JAVA_BIN_PATH" ]; then
      JAVA_LOCATION=`readlink -f $JAVA_BIN_PATH | xargs dirname | xargs dirname | xargs dirname`
      if [ -x "$JAVA_LOCATION/bin/java" ]; then
        PYCHARM_JDK=$JAVA_LOCATION
      fi
    fi
  fi
  if [ -z "$PYCHARM_JDK" ]; then
    echo ERROR: cannot start PyCharm.
    echo No JDK found to run PyCharm. Please validate either PYCHARM_JDK, JDK_HOME or JAVA_HOME environment variable points to valid JDK installation.
    echo
    echo Press Enter to continue.
    read IGNORE
    exit 1
  fi
fi

VERSION_LOG='/tmp/java.version.log'
$PYCHARM_JDK/bin/java -version 2> $VERSION_LOG
grep 'OpenJDK' $VERSION_LOG
OPEN_JDK=$?
grep '64-Bit' $VERSION_LOG
BITS=$?
rm $VERSION_LOG
if [ $OPEN_JDK -eq 0 ]; then
  echo WARNING: You are launching IDE using OpenJDK Java runtime
  echo
  echo          THIS IS STRICTLY UNSUPPORTED DUE TO KNOWN PERFORMANCE AND GRAPHICS PROBLEMS
  echo
  echo NOTE:    If you have both Sun JDK and OpenJDK installed
  echo          please validate either PYCHARM_JDK or JDK_HOME environment variable points to valid Sun JDK installation
  echo
  echo Press Enter to continue.
  read IGNORE
fi
if [ $BITS -eq 0 ]; then
  BITS="64"
else
  BITS=""
fi

#--------------------------------------------------------------------------
#   Ensure the PYCHARM_HOME var for this script points to the
#   home directory where PyCharm is installed on your system.

SCRIPT_LOCATION=$0
# Step through symlinks to find where the script really is
while [ -L "$SCRIPT_LOCATION" ]; do
  SCRIPT_LOCATION=`readlink -e "$SCRIPT_LOCATION"`
done

PYCHARM_HOME=`dirname "$SCRIPT_LOCATION"`/..
PYCHARM_BIN_HOME=`dirname "$SCRIPT_LOCATION"`

export JAVA_HOME
export PYCHARM_HOME

if [ -n "$PYCHARM_PROPERTIES" ]; then
  PYCHARM_PROPERTIES_PROPERTY=-Didea.properties.file=$PYCHARM_PROPERTIES
fi

if [ -z "$PYCHARM_MAIN_CLASS_NAME" ]; then
  PYCHARM_MAIN_CLASS_NAME="com.intellij.idea.Main"
fi

if [ -z "$PYCHARM_VM_OPTIONS" ]; then
  PYCHARM_VM_OPTIONS="$PYCHARM_HOME/bin/pycharm.vmoptions"
fi

# isEap
if [ "@@isEap@@" = "true" ]; then
 AGENT="-agentlib:yjpagent$BITS=disablej2ee,sessionname=pycharm"
fi

REQUIRED_JVM_ARGS="-Xbootclasspath/a:../lib/boot.jar -Didea.platform.prefix=Python -Didea.no.jre.check=true -Didea.paths.selector=@@system_selector@@ $AGENT $PYCHARM_PROPERTIES_PROPERTY $REQUIRED_JVM_ARGS"
JVM_ARGS=`tr '\n' ' ' < "$PYCHARM_VM_OPTIONS"`
JVM_ARGS="$JVM_ARGS $REQUIRED_JVM_ARGS"

CLASSPATH=../lib/bootstrap.jar
CLASSPATH=$CLASSPATH:../lib/util.jar
CLASSPATH=$CLASSPATH:../lib/jdom.jar
CLASSPATH=$CLASSPATH:../lib/log4j.jar
CLASSPATH=$CLASSPATH:../lib/extensions.jar
CLASSPATH=$CLASSPATH:../lib/trove4j.jar
CLASSPATH=$CLASSPATH:$PYCHARM_CLASSPATH

export CLASSPATH

LD_LIBRARY_PATH=.:$LD_LIBRARY_PATH
export LD_LIBRARY_PATH

cd "$PYCHARM_BIN_HOME"
while true ; do
  $PYCHARM_JDK/bin/java $JVM_ARGS -Djb.restart.code=88 $PYCHARM_MAIN_CLASS_NAME $*
  test $? -ne 88 && break
done
