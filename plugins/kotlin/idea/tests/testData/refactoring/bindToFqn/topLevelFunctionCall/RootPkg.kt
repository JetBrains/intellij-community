// BIND_TO barFoo
fun foo() {
  <caret>fooBar()
}

fun fooBar() { }

fun barFoo() { }