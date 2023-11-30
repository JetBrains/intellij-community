// NEW_NAME: plus
// RENAME: member
class A {
    operator fun <caret>invoke(n: Int, s: String) = 1
}

fun test() {
    A()(1, "2")
}