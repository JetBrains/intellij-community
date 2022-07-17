fun foo(a: Any) {
    val x = <caret>when (a) {
        is Int -> 1
        is String -> 2
        else -> 3
    }
}

// EXPECTED_LEGACY: null