// BIND_TO test.bar.B
package test.bar

interface A { }

class B : A { }

fun foo() {
    val x: test.bar.<caret>A = B()
}