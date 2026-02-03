#!/bin/bash

test2+="${test1//\`$tmp\`/test\`${tmp}_tmp\`}"
# echo
echo "$test1"