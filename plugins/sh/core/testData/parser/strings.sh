echo ") \\"
echo "    ((r)->direct) ? \\"
echo -n "	bus_${n}((r)->res"
echo $'\101\102\103\010'
echo $'this is \'a single string\'\' it?'
"'" 1234 "'"

' "its also not a double string" '

"a$"
$

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
"s/.*\(iphoneos(([0-9]|.)+)\).*/\1/"
# "$("s/(/")" #todo check s/(
\.
\n
\>
\<
"||"
"&&"
#"$(&&)"  # todo: michail
a#%%
a#%%[0-9]
echo level%%[a-zA-Z]*
[ \${a} ]
[  ]
#[ ] # todo
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
echo 'this is \'a s isn\'t it?'