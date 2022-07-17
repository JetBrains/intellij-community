fun foo() {
    val a = i<caret>f (2 + 2 == 4) 1 else 3
}

// EXPECTED: if (2 + 2 == 4) 1 else 3
// EXPECTED_LEGACY: null