// "Replace 'mapOf(1 to 2)' with 'mutableMapOf(1 to 2)'" "false"
// K2_AFTER_ERROR: RETURN_TYPE_MISMATCH
// K2_ERROR: RETURN_TYPE_MISMATCH
fun foo(): MutableList<Int> {
    return map<caret>Of(1 to 2)
}

