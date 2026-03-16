// "Replace 'mapOf(1 to 2)' with 'mutableMapOf(1 to 2)'" "false"
// K2_ERROR: Return type mismatch: expected 'MutableList<Int>', actual 'Map<Int, Int>'.
// K2_AFTER_ERROR: Return type mismatch: expected 'MutableList<Int>', actual 'Map<Int, Int>'.
fun foo(): MutableList<Int> {
    return map<caret>Of(1 to 2)
}

// IGNORE_K1