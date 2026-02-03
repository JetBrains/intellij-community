// ERROR: Incompatible types: String and Int
// "Change type of called function 'toString' to 'Int'" "false"
// WITH_STDLIB
// K2_AFTER_ERROR: Incompatible types 'Int' and 'String'.
fun test(i: Int) {
    when (i) {
        "".<caret>toString() -> {}
    }
}