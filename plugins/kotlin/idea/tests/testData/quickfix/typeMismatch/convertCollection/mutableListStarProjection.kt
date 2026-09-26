// "Replace 'emptyList<Int>()' with 'mutableListOf<Int>()'" "false"
// K2_AFTER_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_ERROR: ARGUMENT_TYPE_MISMATCH

fun foo(list: MutableList<*>) {}

fun bar() {
    foo(emptyList<Int><caret>())
}

