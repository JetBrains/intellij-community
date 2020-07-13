

wait_file() {
  local file="$1"
  until test $((wait_seconds--)) -eq 0 -o -f "$file" ; do sleep 1; done
#  ((++wait_seconds))
}

function wait_file() {
  local file="$1"
  until test $((wait_seconds--)) -eq 0 -o -f "$file" ; do sleep 1; done
#  ((++wait_seconds))
}

function wait_file {
  local file="$1"
  until test $((wait_seconds--)) -eq 0 -o -f "$file" ; do sleep 1; done
#  ((++wait_seconds))
}

varr(     ) { ''
} | echo 1 # what a wonderful language

`test -z "${DGIF}" && echo DGIF`
`test -z "${DGIF}"`
test -z `"${DGIF}"`