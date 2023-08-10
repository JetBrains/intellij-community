fun foo() {
    <caret>bar()
}

fun bar() = 1

// DISALLOW_METHOD_CALLS
// EXPECTED: null