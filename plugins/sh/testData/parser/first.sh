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

name='name'

dir=$name.git $(( 1+2 ))
dir3=$name.git$(( 1+2 ))

cd -

dir3=$name.git$(( 1-2 ))

echo $dir
echo $dir3

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

$(find . -type f -size +0c ! -name '*[0-9]*' \
     ! -name '\.*' ! -name '*conf' -maxdepth 1 -print | sed 's/^\.\///')


for name in $(find . -type f -size +0c ! -name '*[0-9]*' \
     ! -name '\.*' ! -name '*conf' -maxdepth 1 -print | sed 's/^\.\///')
do
  echo 1
done

echo \(analyzed $(wc -l < $stats) netstat log entries for calculations\)

echo analyzed for calculations
echo analyzed for done do while calculations

echo {ex,edit}
echo {{}}
find "$DIR" -name "*~" -exec rm -f {} \;
chown root /usr/{ucb/{ex,edit},lib/{ex?.?*,how_ex}}

. ./movecach.sh
source ./foo.sh

. movecach.sh
source foo.sh

f=1 b=2 cat movecach.sh

# init
file="current_line.txt"
let count=0
let indx+=1
let indx-=1
let indx=1*1
let a=1 b=1

typeset function="$1"
typeset function+="$1"
typeset name="$2"
typeset keys="$3"




