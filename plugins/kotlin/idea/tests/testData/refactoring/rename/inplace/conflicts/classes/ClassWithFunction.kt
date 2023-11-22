// NEW_NAME: f
// RENAME: member

class <caret>A(i: Int)
fun f() {}

fun test() {
    f()
    val aClass = A(0)
}
