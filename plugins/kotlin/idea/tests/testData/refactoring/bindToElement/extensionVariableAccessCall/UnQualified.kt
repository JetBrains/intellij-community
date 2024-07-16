// BIND_TO test.barFoo
package test

fun foo() {
    0.<caret>fooBar()
}

val fooBar: Any.() -> Unit = { }

val barFoo: Any.() -> Unit = { }