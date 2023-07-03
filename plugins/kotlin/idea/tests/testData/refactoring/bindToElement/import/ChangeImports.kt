// BIND_TO test.bar.A
package test

import test.foo.A

fun foo() {
    val x = <caret>A()
}