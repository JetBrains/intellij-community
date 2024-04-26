// NEW_NAME: f
// RENAME: member
fun f(i: Int) {}
fun <caret>b() {}

fun test() {
    f(42)
    b()
}

