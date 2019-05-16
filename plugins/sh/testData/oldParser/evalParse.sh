eval 'a=1'
eval "a=1"
eval "echo" "abc" "$1"
eval "" ""
y=`eval ls -l`  #  Similar to y=`ls -l`
echo $y
eval array_member=\${arr${array_number}[element_number]}
eval `echo args$i`=`cygpath --path --ignore --mixed "$arg"`