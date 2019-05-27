entryFound="$(grep -v '^#' "$usrcff" | grep " $name(")"
value="$(printf "$members\n" | cut -d ',' -f "$position")"
position="$(eval printf '%s\\n' "\$$varname")"