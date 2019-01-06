#!/bin/bash

# Simple line count example, using bash
#
# Bash tutorial: http://linuxconfig.org/Bash_scripting_Tutorial#8-2-read-file-into-bash-array
# My scripting link: http://www.macs.hw.ac.uk/~hwloidl/docs/index.html#scripting
#
# Usage: ./line_count.sh file
# -----------------------------------------------------------------------------

echo "Hello      World"       # This is a comment, too!
echo "Hello World"
echo "Hello * World"
echo Hello * World
echo Hello      World
echo "Hello" World
echo Hello "     " World

file="foo"

echo "Message" | tail 24
echo "Message" | tail 25
echo "Message" | tail -n 25
echo $Message | mail -s "disk report `date`" anny

function installJDK {
    local FILE=$1
    local DIR=$2

    mkdir -p $DIR
    tar -xzf $FILE --strip-components=1 -C $DIR
    rm $FILE
}


echo "Hello "*" World"
echo `hello` world
echo 'hello' world


# Link filedescriptor 10 with stdin
exec 10<&0
# stdin replaced with a file supplied as a first argument
exec < $1
# remember the name of the input file
in=$1

exec /usr/bin/sftp -C $account@$host

eval $1=$answer

# init
file="current_line.txt"
let count=0





