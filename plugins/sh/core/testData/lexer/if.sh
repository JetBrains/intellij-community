#!/bin/bash

if [ "foo" = "foo" ]; then
    echo 1
fi;

if [ "foo" = "foo" ]; then echo 2
fi;

if [ -e /etc/software.properties ]; then
    echo '' >> /opt/buildAgent/conf/buildAgent.properties
    cat /etc/software.properties >> /opt/buildAgent/conf/buildAgent.properties
    rm /etc/software.properties
fi

if [ "$key" = "" ] ; then
    # Use default initialization logic based on configuration in '/etc/inittab'.
    echo -e "Executing \\e[32m/sbin/init\\e[0m as PID 1."
else
    # Print second message on screen.
    cat /etc/msg/03_init_02.txt
fi

if [[ "$VERSION_ID" !=  16.04 ]]
then
    dpkg --list | awk '{ print $2 }' | grep 'linux-image-.*-generic' | grep -v `uname -r` | xargs apt-get -y purge --auto-remove
fi

if [ $(sysctl -n kern.features.ufs_acl 2>/dev/null || echo 0) -eq 0 ]; then
	echo "1..0 # SKIP system does not have UFS ACL support"
	exit 0
fi

if [ -f ${NANO_KERNEL} ] ; then
	KERNCONFDIR="$(realpath $(dirname ${NANO_KERNEL}))"
else
	export KERNCONF="${NANO_KERNEL}"
fi

[ ! -d "$1" ]

[ ! ! ! 1 == 1 ]

[[ ${osvers} -ge 7 ]]
[[ (pwd) ]]
[[ ((pwd)) ]]
[[ ((1+1)) ]]
[[ ${sss} -ge 7 ]]

$[ 1 < 1]
$[ 1 > 1]
$[ 1 >= 1]
$[ 1 <= 1]
$[ 1 == 1]
$[ 1 != 1]
$[ 1++ ]
$[ 1-- ]