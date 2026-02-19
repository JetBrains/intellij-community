// "Replace 'listOf(1)' with 'mutableListOf(1)'" "false"
// K2_AFTER_ERROR: Return type mismatch: expected 'MutableList<String>', actual 'List<Int>'.

fun test(): MutableList<String> {
    return list<caret>Of(1)
}

// IGNORE_K1