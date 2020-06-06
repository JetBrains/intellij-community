if [ !a == $a ]; then
    echo 1
fi;

if [ ! a == $a ]; then
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

if [[ $# < 2 ]]; then
	echo "Usage: [vmlinux] [base path] [modules path]"
fi

if [[ $# > 2 ]]; then
	echo "Usage: [vmlinux] [base path] [modules path]"
fi

if [[ "${USE_PREBUILT_HEXAOGON_BINARIES}" != "true"
      && -z "${QUALCOMM_SDK}" ]]; then
    exit 1
fi

if [[ "${USE_PREBUILT_HEXAOGON_BINARIES}" != "true" &&
      -z "${QUALCOMM_SDK}" ]]; then
    exit 1
fi

if [ "$key" = "" ] ; then
    # Use default initialization logic based on configuration in '/etc/inittab'.
    echo -e "Executing \\e[32m/sbin/init\\e[0m as PID 1."
else
    # Print second message on screen.
    cat /etc/msg/03_init_02.txt
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

if [ -f ${NANO_KERNEL} ] ; then
	KERNCONFDIR="$(realpath $(dirname ${NANO_KERNEL}))"
else
	export KERNCONF="${NANO_KERNEL}"
fi

array=(*.sh)
a=array[1]

echo "${a}"

if test "x$auth_opt" != "x" ; then
	echo $auth_opt >> $OBJ/sshd_proxy
fi