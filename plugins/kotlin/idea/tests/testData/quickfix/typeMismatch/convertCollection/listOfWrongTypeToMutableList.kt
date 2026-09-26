// "Replace 'listOf(1)' with 'mutableListOf(1)'" "false"
// K2_AFTER_ERROR: INITIALIZER_TYPE_MISMATCH
// K2_ERROR: INITIALIZER_TYPE_MISMATCH
fun foo() {
    val list: MutableList<String> = list<caret>Of(1)
}

