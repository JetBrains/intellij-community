#!/bin/bash
count=0

answer () {
while read response; do
echo
case $response in
[yY][eE][sS]|[yY])
printf "$1n"
return 0
#$2
break
;;
[nN][oO]|[nN])
printf "$2n"
return 1
#$4
break
;;
*)
printf "Please, enter Y(yes) or N(no)! "
esac
done
}

trap 'printf "Are you sure to skip? [Y/n] "; answer && printf "nSkipping...nn" && exit 1 ' SIGINT

while [[ $count -lt 100 ]]
do
sleep 1
(( count++ ))
echo $count
done