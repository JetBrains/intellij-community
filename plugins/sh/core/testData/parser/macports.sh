#!/bin/bash
set -eux

if [ -z "${MACPORTS}" ]; then
	exit
fi

VER=`sed "s/MacPorts-\(.*\)-.*-.*.pkg/\1/" <<< $MACPORTS`

curl -L https://github.com/macports/macports-base/releases/download/v$VER/$MACPORTS -o $TMPDIR/$MACPORTS
sudo installer -pkg $TMPDIR/$MACPORTS -target /
sudo /opt/local/bin/port sync
rm $TMPDIR/$MACPORTS

MSG="$(alsactlrestore$CARD2>&1>/dev/null)"