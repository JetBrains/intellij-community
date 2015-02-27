#!/bin/bash

./fetchIdea.sh

# Run the tests
if [ "$1" = "-d" ]; then
    ant -d -f build.xml -DIDEA_HOME=./idea-IC
else
    ant -f build.xml -DIDEA_HOME=./idea-IC
fi

# Was our build successful?
stat=$?

if [ "${TRAVIS}" != true ]; then
    ant -f build.xml -q clean
    rm -rf idea-IC
fi

# Return the build status
exit ${stat}