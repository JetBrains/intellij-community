package bar

fun foo(): Int {
    // TODO: The .Companion parts here can be removed after KT-64842 is fixed
    return Test.Companion.a + Test.Companion.a
}