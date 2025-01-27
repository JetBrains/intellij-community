package test

import test.X.foo

class Y(private val x: X) {
    fun test() {
        1.foo()
        with(1) { foo() }
        with(A()) { bar() }
    }
}