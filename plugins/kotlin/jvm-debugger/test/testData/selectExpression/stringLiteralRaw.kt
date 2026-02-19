fun foo() {
    val x = """f<caret>oo"""
}

// EXPECTED: """"foo""""
// EXPECTED_LEGACY: null