// BIND_TO test.B
package test

object A {
    fun foo() { }
}

object B {
    fun foo() { }
}

fun foo() {
    test.<caret>A.foo()
}