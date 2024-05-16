// BIND_TO test.B.plus
package test

class B {
    operator fun plus(b: B) { }

    fun m() {
        val v = this <caret>+ this
    }
}
