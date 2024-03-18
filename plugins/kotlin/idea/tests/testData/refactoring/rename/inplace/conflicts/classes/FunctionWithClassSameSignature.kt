// NEW_NAME: A
// RENAME: member
// SHOULD_FAIL_WITH: Constructor 'A' is already declared in class 'A'

class A(i: Int)
fun <caret>f(i: Int) {}


fun test() {
    f(42)
    val aClass = A(0)
}

