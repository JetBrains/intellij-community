#!/bin/sh

# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

# -----------------------------------------------------------------------------
# Apache Maven Startup Script
#
# Environment Variable Prerequisites
#
#   JAVA_HOME       Must point at your Java Development Kit installation.
#   MAVEN_OPTS      (Optional) Java runtime options used when Maven is executed.
#   MAVEN_SKIP_RC   (Optional) Flag to disable loading of mavenrc files.
# -----------------------------------------------------------------------------

if [ -z "$MAVEN_SKIP_RC" ] ; then

  if [ -f /etc/mavenrc ] ; then
    . /etc/mavenrc
  fi

  if [ -f "$HOME/.mavenrc" ] ; then
    . "$HOME/.mavenrc"
  fi

fi

# OS specific support. $var _must_ be set to either true or false.
cygwin=false;
mingw=false;
case "`uname`" in
  CYGWIN*) cygwin=true;;
  MINGW*) mingw=true;;
esac

## resolve links - $0 may be a link to Maven's home
PRG="$0"

# need this for relative symlinks
while [ -h "$PRG" ] ; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG="`dirname "$PRG"`/$link"
  fi
done

saveddir=`pwd`

MAVEN_HOME=`dirname "$PRG"`/..

# make it fully qualified
MAVEN_HOME=`cd "$MAVEN_HOME" && pwd`

cd "$saveddir"

# For Cygwin, ensure paths are in Unix format before anything is touched
if $cygwin ; then
  [ -n "$MAVEN_HOME" ] &&
    MAVEN_HOME=`cygpath --unix "$MAVEN_HOME"`
  [ -n "$JAVA_HOME" ] &&
    JAVA_HOME=`cygpath --unix "$JAVA_HOME"`
  [ -n "$CLASSPATH" ] &&
    CLASSPATH=`cygpath --path --unix "$CLASSPATH"`
fi

# For MinGW, ensure paths are in Unix format before anything is touched
if $mingw ; then
  [ -n "$MAVEN_HOME" ] &&
    MAVEN_HOME=`(cd "$MAVEN_HOME"; pwd)`
  [ -n "$JAVA_HOME" ] &&
    JAVA_HOME=`(cd "$JAVA_HOME"; pwd)`
  # TODO classpath?
fi

if [ -z "$JAVA_HOME" ] ; then
  JAVACMD=`which java`
else
  JAVACMD="$JAVA_HOME/bin/java"
fi

if [ ! -x "$JAVACMD" ] ; then
  echo "The JAVA_HOME environment variable is not defined correctly" >&2
  echo "This environment variable is needed to run this program" >&2
  echo "NB: JAVA_HOME should point to a JDK not a JRE" >&2
  exit 1
fi

CLASSWORLDS_JAR=`echo "${MAVEN_HOME}"/boot/plexus-classworlds-*.jar`
CLASSWORLDS_LAUNCHER=org.codehaus.plexus.classworlds.launcher.Launcher

# For Cygwin, switch paths to Windows format before running java
if $cygwin ; then
  [ -n "$MAVEN_HOME" ] &&
    MAVEN_HOME=`cygpath --path --windows "$MAVEN_HOME"`
  [ -n "$JAVA_HOME" ] &&
    JAVA_HOME=`cygpath --path --windows "$JAVA_HOME"`
  [ -n "$CLASSPATH" ] &&
    CLASSPATH=`cygpath --path --windows "$CLASSPATH"`
  [ -n "$CLASSWORLDS_JAR" ] &&
    CLASSWORLDS_JAR=`cygpath --path --windows "$CLASSWORLDS_JAR"`
fi

# traverses directory structure from process work directory to filesystem root
# first directory with .mvn subdirectory is considered project base directory
find_maven_basedir() {
(
  basedir=`find_file_argument_basedir "$@"`
  wdir="${basedir}"
  while [ "$wdir" != '/' ] ; do
    if [ -d "$wdir"/.mvn ] ; then
      basedir=$wdir
      break
    fi
    wdir=`cd "$wdir/.."; pwd`
  done
  echo "${basedir}"
)
}

find_file_argument_basedir() {
(
  basedir=`pwd`

  found_file_switch=0
  for arg in "$@"; do
    if [ ${found_file_switch} -eq 1 ]; then
      if [ -d "${arg}" ]; then
        basedir=`cd "${arg}" && pwd -P`
      elif [ -f "${arg}" ]; then
        basedir=`dirname "${arg}"`
        basedir=`cd "${basedir}" && pwd -P`
        if [ ! -d "${basedir}" ]; then
          echo "Directory ${basedir} extracted from the -f/--file command-line argument ${arg} does not exist" >&2
          exit 1
        fi
      else
        echo "POM file ${arg} specified with the -f/--file command line argument does not exist" >&2
        exit 1
      fi
      break
    fi
    if [ "$arg" = "-f" -o "$arg" = "--file" ]; then
      found_file_switch=1
    fi
  done
  echo "${basedir}"
)
}

# concatenates all lines of a file
concat_lines() {
  if [ -f "$1" ]; then
    echo "`tr -s '\r\n' '  ' < "$1"`"
  fi
}

MAVEN_PROJECTBASEDIR="${MAVEN_BASEDIR:-`find_maven_basedir "$@"`}"
MAVEN_OPTS="`concat_lines "$MAVEN_PROJECTBASEDIR/.mvn/jvm.config"` $MAVEN_OPTS"

# For Cygwin, switch project base directory path to Windows format before
# executing Maven otherwise this will cause Maven not to consider it.
if $cygwin ; then
  [ -n "$MAVEN_PROJECTBASEDIR" ] &&
  MAVEN_PROJECTBASEDIR=`cygpath --path --windows "$MAVEN_PROJECTBASEDIR"`
fi

export MAVEN_PROJECTBASEDIR

# Provide a "standardized" way to retrieve the CLI args that will
# work with both Windows and non-Windows executions.
MAVEN_CMD_LINE_ARGS="$MAVEN_CONFIG $@"
export MAVEN_CMD_LINE_ARGS

exec "$JAVACMD" \
  $MAVEN_OPTS \
  $MAVEN_DEBUG_OPTS \
  -classpath "${CLASSWORLDS_JAR}" \
  "-Dclassworlds.conf=${MAVEN_HOME}/bin/m2.conf" \
  "-Dmaven.home=${MAVEN_HOME}" \
  "-Dlibrary.jansi.path=${MAVEN_HOME}/lib/jansi-native" \
  "-Dmaven.multiModuleProjectDirectory=${MAVEN_PROJECTBASEDIR}" \
  ${CLASSWORLDS_LAUNCHER} "$@"
