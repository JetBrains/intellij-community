// BIND_TO test.B.minus
package test

class B {
    infix operator fun plus(b: B) { }

    infix operator fun minus(b: B) { }

    fun m() {
        val v = this <caret>plus this
    }
}
