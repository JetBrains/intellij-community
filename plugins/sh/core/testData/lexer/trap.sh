#!/bin/bash
count=0

trap 'echo "Exit"; exit 1' 2

while [ $count -lt 100 ]
do
sleep 1
(( count++ ))
echo $count
done


