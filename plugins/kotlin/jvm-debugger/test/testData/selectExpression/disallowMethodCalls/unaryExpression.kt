fun foo() {
    <caret>+1
}

// DISALLOW_METHOD_CALLS
// EXPECTED: null
// IGNORE_K2