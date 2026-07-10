// ERROR: Incompatible types: String and Int
// "Change type of called function 'toString' to 'Int'" "false"
// WITH_STDLIB
// K2_AFTER_ERROR: INCOMPATIBLE_TYPES
// K2_ERROR: INCOMPATIBLE_TYPES
fun test(i: Int) {
    when (i) {
        "".<caret>toString() -> {}
    }
}