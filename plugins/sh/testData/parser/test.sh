`test -z "${DGIF}" && echo DGIF`
`test -z "${DGIF}"`
test -z `"${DGIF}"`
test -s $(echo abc) || CONCURRENCY="none"

# IDEA-244312
echo "$(test)"
echo "$(echo test)"
echo hello