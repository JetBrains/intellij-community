// BIND_TO barFoo
fun foo() {
  0.<caret>fooBar
}

val Any.fooBar: Any? = null

val Any.barFoo: Any? = null