// BIND_TO test.barFoo
package test

fun foo() {
    0.<caret>fooBar
}

val Any.fooBar: Any? = null

val Any.barFoo: Any? = null