// "Replace 'setOf<Int>()' with 'mutableSetOf<Int>()'" "false"
// K2_AFTER_ERROR: Assignment type mismatch: actual type is 'Set<Int>', but 'MutableSet<String>' was expected.

fun test() {
    val strs: MutableSet<String>
    strs =<caret> setOf<Int>()
}

// IGNORE_K1