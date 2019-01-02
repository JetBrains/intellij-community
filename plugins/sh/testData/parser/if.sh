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