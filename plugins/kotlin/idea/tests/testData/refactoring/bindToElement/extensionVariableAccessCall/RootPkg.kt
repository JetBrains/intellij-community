// BIND_TO barFoo
fun foo() {
  0.<caret>fooBar()
}

val fooBar: Any.() -> Unit = { }

val barFoo: Any.() -> Unit = { }