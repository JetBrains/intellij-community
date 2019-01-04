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

((1+1+1==v))
(( 1+1+1==v )) && echo 2 >> /dev/null
((1+1+1==v)) >> /dev/null
echo 1 >> /dev/null

echo $[1+2]

$[123]

echo "$[1+2]"
echo '$[1+2]'
