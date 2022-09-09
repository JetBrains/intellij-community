#IDEA-219928
arg="${arg//\\/\\\\}"
printf "%s\n" "${arg}"

#IDEA-243118
"\"${string//\"/\\\"}\""