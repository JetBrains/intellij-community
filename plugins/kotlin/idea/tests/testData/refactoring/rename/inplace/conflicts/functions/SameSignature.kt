// NEW_NAME: f
// RENAME: member
// SHOULD_FAIL_WITH: Function 'f' is already declared in package
fun f(i: Int) {}
fun <caret>b(i: Int) {}

fun test() {
    f(42)
    b(0)
}

