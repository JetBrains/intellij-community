// "Replace 'listOf(1)' with 'mutableListOf(1)'" "false"
// K2_ERROR: Return type mismatch: expected 'MutableList<Int>', actual 'List<Int>'.
// K2_AFTER_ERROR: Return type mismatch: expected 'MutableList<Int>', actual 'List<Int>'.

fun <T> listOf(vararg elements: T): List<T> = emptyList()

fun foo(): MutableList<Int> {
    return list<caret>Of(1)
}

// IGNORE_K1