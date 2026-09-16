// NEW_NAME: A
// RENAME: member

class A(i: Int)
fun <caret>f() {}

fun test() {
    f()
    val aClass = A(0)
}
