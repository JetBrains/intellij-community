#!/bin/bash

var=' "hi mom"'

if test ! -z "$var"; then
    echo $var
fi

var="${var//[[:space:]\"]/}"

if test ! -z "$var"; then
    echo $var
fi