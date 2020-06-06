[[ a = b ]]

[[ a = b || c = d ]]

[[ a = b && c = d ]]

[[ a = b && c = d || e = f && g = h ]]

[[ a = b || c = d && e = f || g = h ]]

[[ (a) ]]
[[ (a = b) ]]
[[ (a = b) && (c = d) ]]
[[ (a = b) || (c = d) ]]
[[ (a = b) || (c = d) || (e = f) || (g = h) ]]

[[ a = b && (c = d) ]]
[[ (a = b) && c = d ]]

[[ a = b || (c = d) ]]
[[ (a = b) || c = d ]]

[[ a = b && (c = d || e = f) ]]

# binary conditional expressions
[[ file.txt -ef file.txt ]]
[[ file.txt -nt file.txt ]]
[[ file.txt -ot file.txt ]]
[[ $file -ot $otherFile ]]

[ -z "$a" -a -z "$b" ]
[[ -z "$a" && -z "$b" ]]

# unary conditional expressions
[[ -a file.txt ]]
[[ -a "file.txt" ]]
[[ -a 'file.txt' ]]
[[ -a $file ]]
[[ -a ${basename}$ext ]]

# literals
[[ 1 ]]
[[ ! 1 ]]
[[ a ]]
[[ ! a ]]
[[ "a" ]]
[[ ! "a" ]]