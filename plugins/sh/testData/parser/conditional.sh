#!/bin/bash

echo "foo: $res"

echo test -a foo.txt

if test -a foo.txt; then
  echo "foo"
fi;

if [ !a == $a ]; then
    echo 1
fi;

if [ 1 == 1 ] ;then
 echo
fi;

if [ 1 == 1 ]; then
    echo 1
fi;

if [ "1" == "1" ]; then
    echo 1
fi;

if [ "foo" = "foo" ]; then
    echo 1
fi;

if [ "foo" = "foo" ]; then echo 2
fi;

if [ "$PACKER_BUILD_NAME" == "virtualbox" ]; then
  SERVER_URL="${TEAMCITY_SERVER}"
else
  SERVER_URL=
fi

if [ 1 == 1 ]; then
    echo 1
else
    echo 2
fi

if [ "$PACKER_BUILD_NAME" == "virtualbox" ]; then
#  SERVER_URL="${TEAMCITY_SERVER}"
    echo 1
else
  SERVER_URL=
fi

cat $0 > /dev/null || cat $0 > /dev/null

[ 1 == 1 ]

[[ 1 == 1+1 ]] | cat

[[ 123 == 123 ]] | echo

if [ ] || [ ]; then
    echo 1
fi

if cat $0 > /dev/null || cat $0 > /dev/null
   echo 1 ; then
 echo
fi

[ ! ! !  1 == 1 ]

[ ! -d "$1" ]

[[ "$key" = "" ]
[ "$key" = "" ]]

[[ ${osvers} -ge 7 ]]
[[ (pwd) ]]
[[ ${sss} -ge 7 ]]

function test() {
    echo my custom test function $3
}
test