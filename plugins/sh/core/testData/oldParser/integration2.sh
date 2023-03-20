if [ startpar = "$CONCURRENCY" ] ; then
    test -s /etc/init.d/.depend.boot  || CONCURRENCY="none"
    test -s /etc/init.d/.depend.start || CONCURRENCY="none"
fi