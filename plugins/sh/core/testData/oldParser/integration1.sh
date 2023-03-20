if [ -e /lib/init/splash-functions-base ] ; then
    . /lib/init/splash-functions-base
else
    # Quiet down script if old initscripts version without /lib/init/splash-functions-base is used.
    splash_progress() { return 1; }
fi