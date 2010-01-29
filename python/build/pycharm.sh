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
  if [ -z "$PYCHARM_JDK" ]; then
    echo ERROR: cannot start PyCharm.
    echo No JDK found to run PyCharm. Please validate either PYCHARM_JDK or JDK_HOME points to valid JDK installation
  fi
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

REQUIRED_JVM_ARGS="-Xbootclasspath/a:../lib/boot.jar -Didea.platform.prefix=Python -Didea.no.jre.check=true $PYCHARM_PROPERTIES_PROPERTY $REQUIRED_JVM_ARGS"
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
exec $PYCHARM_JDK/bin/java $JVM_ARGS $PYCHARM_MAIN_CLASS_NAME $*
