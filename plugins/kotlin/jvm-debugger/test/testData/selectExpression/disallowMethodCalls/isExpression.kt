fun foo() {
    1 <caret>is Int
}

// DISALLOW_METHOD_CALLS
// EXPECTED: 1 is Int
// EXPECTED_LEGACY: null