// BIND_TO org.jetbrains.B
package org.jetbrains

class A { }

class B { }

fun foo() {
    val x = <caret>A()
}