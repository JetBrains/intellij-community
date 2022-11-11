eval "$a=()"
eval "printf '%s\n' \"\${${varname}[@]}\""
eval "${varname}+=(\"$REPLY\")"