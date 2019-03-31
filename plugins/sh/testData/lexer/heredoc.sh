#!/bin/sh

cat <<EO\+\\F >> /opt/buildAgent/conf/buildAgent.properties
intellij.can.build.default.branch=true
intellij.build.branch.pattern=master
EO\+\\F