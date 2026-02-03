fun foo() {
    val a = 1
    <caret>a foo 1
}

fun Int.foo(i: Int) = 1

// DISALLOW_METHOD_CALLS
// EXPECTED: a