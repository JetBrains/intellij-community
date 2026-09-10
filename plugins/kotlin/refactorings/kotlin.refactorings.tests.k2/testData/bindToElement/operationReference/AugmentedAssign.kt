// BIND_TO test.B.minusAssign
package test

class B {
    operator fun plusAssign(b: B) { }

    operator fun minusAssign(b: B) { }
}

fun m() {
    var b = B()
    b +<caret>= b
}
