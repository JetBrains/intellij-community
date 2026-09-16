// BIND_TO test.B.div
package test

class B {
    operator fun times(b: B) { }

    operator fun div(b: B) { }
}

fun m() {
    var b = B()
    b *<caret>= b
}
