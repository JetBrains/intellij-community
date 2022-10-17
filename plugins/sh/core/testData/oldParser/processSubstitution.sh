cat <(printf '%s\n' "${BEFORE[@]}" | LC_ALL=C sort)
while read line ; do echo $line ; done < <(echo :)
echo <(echo a) $var
