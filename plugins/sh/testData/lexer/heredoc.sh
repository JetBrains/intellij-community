#!/bin/sh

cat <<EOT >> /opt/buildAgent/conf/buildAgent.properties
intellij.can.build.default.branch=true
intellij.build.branch.pattern=master
EOT