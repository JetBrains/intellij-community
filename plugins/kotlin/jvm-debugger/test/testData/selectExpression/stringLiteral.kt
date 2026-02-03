fun foo() {
    val x = "foo<caret>"
}

// EXPECTED: ""foo""
// EXPECTED_LEGACY: null