#! /bin/bash 
#
#     JFlex start script $Revision: 2.0 $
#
# if Java is not in your binary path, you need to supply its
# location in this script. The script automatically finds 
# JFLEX_HOME when called directly, via binary path, or symbolic
# link. 
#
# Site wide installation: simply make a symlink from e.g.
# /usr/bin/jflex to this script at its original position
#
#===================================================================
#
# configurables:

# path to the java interpreter
JAVA=java

# end configurables
#
#===================================================================
#

# calculate true location

PRG=`type $0`
PRG=${PRG##* }

# If PRG is a symlink, trace it to the real home directory

while [ -L "$PRG" ]
do
    newprg=$(ls -l ${PRG})
    newprg=${newprg##*-> }
    [ ${newprg} = ${newprg#/} ] && newprg=${PRG%/*}/${newprg}
    PRG="$newprg"
done

PRG=${PRG%/*}
JFLEX_HOME=${PRG}/.. 

# --------------------------------------------------------------------

export CLASSPATH
CLASSPATH=$JFLEX_HOME/lib/JFlex.jar

$JAVA JFlex.Main $@

#for more memory:
#$JAVA -Xmx128m JFlex.Main $@
