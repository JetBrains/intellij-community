#!/bin/bash

for i in 1 2 3 4 5
do
   echo "Welcome $i times"
done

for (( c=1; c<=5; c++ ))
do
   echo "Welcome $c times"
done

for file in $(ls)
do
	du $file
done

for (( val=1; $val < $len ; ))
do
  echo 1
done

for (( ;  ; ))
do
      break;
done

for (( ;  ; ))
do
done

for (( ;  ; ))
{
}


for name in idea-ultimate idea-community idea-contrib idea-community-android idea-community-android-tools idea-cidr idea-appcode-testdata idea-gemsData; do
	dir=$name.git
	echo "$name.git = $name.git" >> $GIT_DIR/map
done


// todo: fix me

for i in {1..5}
do
   echo "Welcome $i times"
done

echo "Bash version ${BASH_VERSION}..."
for i in {0..10..2}
do
     echo "Welcome $i times"
done

for i in $(seq 1 2 20)
do
   echo "Welcome $i times"
done
