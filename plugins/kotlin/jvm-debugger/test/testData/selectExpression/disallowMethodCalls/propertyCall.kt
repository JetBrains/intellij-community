val a = 1
fun foo() {
    <caret>a
}

// DISALLOW_METHOD_CALLS
// EXPECTED: a