// NEW_NAME: A
// RENAME: member
// SHOULD_FAIL_WITH: Function 'f' will be shadowed by constructor 'A'

class A(i: Int)
fun <caret>f(i: Int) {}


fun test() {
    f(42)
    val aClass = A(0)
}

