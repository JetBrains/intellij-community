exists() {
  decho
}

decho() {
  echo "Sample text"
  exists
}

foo() {
  bar() {
    exists
  }
  exists
}

foo
decho
<caret>exists