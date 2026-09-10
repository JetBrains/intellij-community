// BIND_TO test.barFoo
package test

fun foo() {
    0.<caret>fooBar()
}

val fooBar = fun Any.(): Unit {}

val barFoo = fun Any.(): Unit {}