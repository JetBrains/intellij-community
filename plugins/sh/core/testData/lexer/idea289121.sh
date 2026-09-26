#!/bin/bash

var1='"foo ": "bar"'
var2=${var1##*\": \"}
echo "This should be highlighted in green"