fun foo() {
    <caret>bar { }
}

fun bar(f: () -> Unit) = 1

// DISALLOW_METHOD_CALLS
// EXPECTED: null