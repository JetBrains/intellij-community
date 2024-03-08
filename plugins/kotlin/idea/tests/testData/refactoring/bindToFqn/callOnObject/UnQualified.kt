// BIND_TO test.B
package test

object A {
    fun foo() { }
}

object B {
    fun foo() { }
}

fun foo() {
    <caret>A.foo()
}