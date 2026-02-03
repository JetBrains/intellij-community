fun foo() {
    val x = """f<caret>oo"""
}

// DISALLOW_METHOD_CALLS
// EXPECTED: null