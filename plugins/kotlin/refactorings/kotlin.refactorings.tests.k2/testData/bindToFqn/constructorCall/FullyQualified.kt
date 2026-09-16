// BIND_TO test.bar.B
package test.bar

class A { }

class B { }

fun foo() {
    val x = test.bar.<caret>A()
}