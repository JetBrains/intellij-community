// NEW_NAME: f
// RENAME: member
// SHOULD_FAIL_WITH: Function 'f' is already declared in package
class <caret>A(i: Int)
fun f(i: Int) {}


fun test() {
    f(42)
    val aClass = A(0)
}

