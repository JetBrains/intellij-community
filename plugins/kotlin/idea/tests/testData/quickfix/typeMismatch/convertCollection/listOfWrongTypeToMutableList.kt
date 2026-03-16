// "Replace 'listOf(1)' with 'mutableListOf(1)'" "false"
// K2_AFTER_ERROR: Initializer type mismatch: expected 'MutableList<String>', actual 'List<Int>'.
// K2_ERROR: Initializer type mismatch: expected 'MutableList<String>', actual 'List<Int>'.
fun foo() {
    val list: MutableList<String> = list<caret>Of(1)
}

// IGNORE_K1