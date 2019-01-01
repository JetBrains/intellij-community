#!/bin/bash

echo $((1+(++1)))

x=5
y=10
ans=$(( x + y ))
echo "$x + $y = $ans"

echo "$((1+1+2))"

v=$((1))

name=[value]

echo ${1}
echo ${ans}


#echo ${ans:1}
#echo ${lsls:1:1}
#echo ${lsls:+1:[01]}
#echo ${lsls:-1:[01]}
#echo ${lsls:?gg:[01]}

echo "${ls}"

echo "$(( 3 * ( 2 + 1 ) ))"