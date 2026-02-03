// BIND_TO test.B.minus
package test

class B {
    operator fun plus(b: B) { }

    operator fun minus(b: B) { }

    fun m() {
        val v = this <caret>+ this
    }
}
