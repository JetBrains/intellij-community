// BIND_TO test.barFoo
package test

fun foo() {
    0.<caret>fooBar()
}

fun Any.fooBar() { }

fun Any.barFoo() { }