exists() {
  echo "Text one"
}
exists

decho() {
  exists() {
    echo "Text two"
  }
  <caret>exists
}