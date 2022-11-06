mysql <<< "CREATE DATABASE dev" || echo hi
mysql <<<"CREATE DATABASE dev"||echo hi
mysql <<< 'CREATE DATABASE dev' || echo hi
mysql <<<"CREATE DATABASE dev"||echo hi
mysql <<< "CREATE DATABASE dev" && echo hi

output=$(tr <<< [[{$op[[${branch}\\<<<]]+={}   sdfsdfsd)
output=$(tr  <<< [[{$op[[${branch}\\<<<]]+={}   );

output=$(tr  <<< '[[8+8]][[${branch}\\)]$\&\&]+={}    p'  );
output=$(tr <<< "[[8+8]][[${branch}\\)]$\&\&]+={}    p"  );
if [ ! ed <<<Q ]; then echo Some text; fi