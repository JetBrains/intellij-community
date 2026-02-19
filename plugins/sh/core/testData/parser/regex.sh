if [[ "$JAVAC_VERSION" =~ javac\ (1\.([789]|[1-9][0-9])).*$ ]]; then
  XCODE_VERSION="${XCODE_VERSION}.0"
fi

if [[ $# == 1 && ($1 =~ "b   " || $1 == "a") ]]; then
  echo "pong"
fi