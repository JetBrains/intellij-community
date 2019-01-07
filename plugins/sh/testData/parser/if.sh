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

if [ -e /etc/software.properties ]; then
    echo '' >> /opt/buildAgent/conf/buildAgent.properties
    cat /etc/software.properties >> /opt/buildAgent/conf/buildAgent.properties
    rm /etc/software.properties
fi

if [ ]; then
    echo 1
fi

if [[ "$VERSION_ID" !=  16.04 ]]
then
    dpkg --list | awk '{ print $2 }' | grep 'linux-image-.*-generic' | grep -v `uname -r` | xargs apt-get -y purge --auto-remove
fi

if [ "10.12.6" = `defaults read loginwindow SystemVersionStampAsString` ]; then
	curl https://bootstrap.pypa.io/get-pip.py | sudo python
	sudo /usr/local/bin/pip install sh
else
  sudo easy_install sh
fi

echo [ foo[1] ]
echo [foo[1]]