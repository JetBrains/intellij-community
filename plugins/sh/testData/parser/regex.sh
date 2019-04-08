if [[ "$JAVAC_VERSION" =~ javac\ (1\.([789]|[1-9][0-9])).*$ ]]; then
  XCODE_VERSION="${XCODE_VERSION}.0"
fi