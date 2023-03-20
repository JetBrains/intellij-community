fun foo() {
    1.<caret>foo()
}

fun Int.foo() = 1

// DISALLOW_METHOD_CALLS
// EXPECTED: null