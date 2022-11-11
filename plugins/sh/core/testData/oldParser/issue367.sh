#!/usr/bin/env bash

branch=$(git rev-parse --abbrev-ref HEAD 2>/dev/null);
output=$(tr [:lower:] [:upper:] <<< [${branch}]);


if [ -n ${branch} ] && ! [[ $(< $1) =~ $output ]] && [ ${branch} != 'master' ];
    then echo "$output $(< $1)" > $1;
fi

[[ $(< $1) ]]