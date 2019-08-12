#IDEA-219928
arg="${arg//\\/\\\\}"
printf "%s\n" "${arg}"

arg="${arg//\\/\\\\\}"
printf "%s\n" "${arg} ff}"