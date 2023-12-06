// BIND_TO test.B
package test

object A {
    val foo: String = ""
}

object B {
    val foo: String = ""
}

fun foo() {
    <caret>A.foo
}