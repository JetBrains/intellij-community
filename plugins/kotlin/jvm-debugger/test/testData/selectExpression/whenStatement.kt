fun foo(a: Any) {
    <caret>when (a) {
        is Int -> print("foo")
        is String -> print("bar")
    }
}

// EXPECTED_LEGACY: null