fun foo() {
    1 <caret>foo 1
}

fun Int.foo(i: Int) = 1

// DISALLOW_METHOD_CALLS
// EXPECTED: null