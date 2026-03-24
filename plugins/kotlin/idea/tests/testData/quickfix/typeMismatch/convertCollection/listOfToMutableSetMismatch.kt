// "Replace 'listOf(1)' with 'mutableListOf(1)'" "false"
// "Replace 'listOf(1)' with 'mutableSetOf(1)'" "false"
// K2_ERROR: Return type mismatch: expected 'MutableSet<Int>', actual 'List<Int>'.
// K2_AFTER_ERROR: Return type mismatch: expected 'MutableSet<Int>', actual 'List<Int>'.
fun foo(): MutableSet<Int> {
    return list<caret>Of(1)
}

// IGNORE_K1