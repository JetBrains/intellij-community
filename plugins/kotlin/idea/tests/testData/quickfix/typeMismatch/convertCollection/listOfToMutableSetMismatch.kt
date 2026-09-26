// "Replace 'listOf(1)' with 'mutableListOf(1)'" "false"
// "Replace 'listOf(1)' with 'mutableSetOf(1)'" "false"
// K2_AFTER_ERROR: RETURN_TYPE_MISMATCH
// K2_ERROR: RETURN_TYPE_MISMATCH
fun foo(): MutableSet<Int> {
    return list<caret>Of(1)
}

