// BIND_TO test.B
package test

class A {
    fun foo() { }
}

class B {
    fun foo() { }
}

fun foo() {
    val x = test.<caret>A::foo
}