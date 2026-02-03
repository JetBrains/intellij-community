mysql <<< "CREATE DATABASE dev" || echo hi
mysql <<<"CREATE DATABASE dev"||echo hi
mysql <<< 'CREATE DATABASE dev' || echo hi
mysql <<<"CREATE DATABASE dev"||echo hi
mysql <<< "CREATE DATABASE dev" && echo hi
cmd <<< 'hi'; echo hi2
cmd <<< 'hi';echo hi2
cmd <<< 'hi';echo && echo
cmd <<< 'hi' & echo && echo
cmd <<< 'hi'& echo && echo
cmd <<< 'hi'&echo && echo
