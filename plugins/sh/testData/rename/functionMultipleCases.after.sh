exit() {
  decho
}

decho() {
  echo "Sample text"
  exit
}

foo() {
  bar() {
    exit
  }
  exit
}

foo
decho
exit