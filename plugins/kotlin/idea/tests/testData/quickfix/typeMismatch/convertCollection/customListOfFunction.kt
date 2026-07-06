// "Replace 'listOf(1)' with 'mutableListOf(1)'" "false"
// K2_AFTER_ERROR: RETURN_TYPE_MISMATCH
// K2_ERROR: RETURN_TYPE_MISMATCH

fun <T> listOf(vararg elements: T): List<T> = emptyList()

fun foo(): MutableList<Int> {
    return list<caret>Of(1)
}

