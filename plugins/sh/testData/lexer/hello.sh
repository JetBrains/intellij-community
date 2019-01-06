#!/bin/sh
# This is a comment!
echo "Hello      World"       # This is a comment, too!
echo "Hello World"
echo "Hello * World"
echo Hello * World
echo Hello      World
echo "Hello" World
echo Hello "     " World
echo "Hello "*" World"
echo `hello` world
echo 'hello' world


echo pre{one,two,three}post
echo pre{one, wo, three}post
echo /usr/local/src/bash/{old,new,dist,bugs}
echo root /usr/{ucb/{ex,edit},lib/{ex?.?*,how_ex}}

echo `cat $0`
echo `cat \`cat $0\``
echo `cat `cat $0``

exec /usr/bin/sftp -C $account@$host

for name in $(find . -type f -size +0c ! -name '*[0-9]*' \
     ! -name '\.*' ! -name '*conf' -maxdepth 1 -print | sed 's/^\.\///')
do