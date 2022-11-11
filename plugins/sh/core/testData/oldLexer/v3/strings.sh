echo ") \\"
echo "    ((r)->direct) ? \\"
echo -n "	bus_${n}((r)->res"
echo $'\101\102\103\010'
echo $'this is \'a single string\'\' it?'
""
"abc"
"abc""abc"
"\."
"\n"
" "
"$( a )"
"$a"
"a b\""
a b "a b \"" "a" b
"a$"
"$("hey there")"
"$(echo \"\")"
"$(echo \"\")" a
"$(echo || echo)"
"$(echo && echo)"
"$(abc)"
"$(1)"
"$((1))"
"$("s/(/")"
\.
\n
\>
\<
"||"
"$(||)"
"&&"
"$(&&)"
a#%%
a#%%[0-9]
echo level%%[a-zA-Z]*
[ \${a} ]
[  ]
[ ]
[ a  ]
[ a | b ]
[[ a || b ]]
${rdev%:*?}
${@!-+}
${a[@]}
${\ }
$""
$"abc"
$''
$'abc'
"(( $1 ))"
"a
b
c"
"
"
'a
b
c'
'
'
\*
\ 
\{
\;
\.
\r
\n
\:
\(
\)
\"
\\
\>
\<
\$
\ 
\?
\!
abc\
abc
echo 'word \''
echo 'this is \'a s isn\'t it?'