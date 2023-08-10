${a}
${a:a}
${a:-a}
${a:.*}
${a[@]}
${a[*]}
${level%%[a-zA-Z]*}
${a[var]}
${a[var+var2]}
${a[var+var2+1]#[a-z][0]}
${#a[1]}
${a-`$[1]`}
${\}}


$(( ${param} ))