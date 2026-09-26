// "Replace 'setOf<Int>()' with 'mutableSetOf<Int>()'" "false"
// K2_AFTER_ERROR: ASSIGNMENT_TYPE_MISMATCH
// K2_ERROR: ASSIGNMENT_TYPE_MISMATCH

fun test() {
    val strs: MutableSet<String>
    strs =<caret> setOf<Int>()
}

