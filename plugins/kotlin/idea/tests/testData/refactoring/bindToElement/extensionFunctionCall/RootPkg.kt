// BIND_TO barFoo
fun foo() {
  0.<caret>fooBar()
}

fun Any.fooBar() { }

fun Any.barFoo() { }