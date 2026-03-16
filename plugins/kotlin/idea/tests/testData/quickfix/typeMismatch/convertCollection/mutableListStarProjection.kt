// "Replace 'emptyList<Int>()' with 'mutableListOf<Int>()'" "false"
// K2_ERROR: Argument type mismatch: actual type is 'List<Int>', but 'MutableList<*>' was expected.
// K2_AFTER_ERROR: Argument type mismatch: actual type is 'List<Int>', but 'MutableList<*>' was expected.

fun foo(list: MutableList<*>) {}

fun bar() {
    foo(emptyList<Int><caret>())
}

// IGNORE_K1