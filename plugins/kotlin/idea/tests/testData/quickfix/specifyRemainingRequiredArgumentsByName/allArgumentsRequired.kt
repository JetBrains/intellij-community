// "Specify remaining required arguments by name" "false"
// ERROR: No value passed for parameter 'a'
// ERROR: No value passed for parameter 'b'
// ERROR: No value passed for parameter 'c'
// K2_AFTER_ERROR: No value passed for parameter 'a'.
// K2_AFTER_ERROR: No value passed for parameter 'b'.
// K2_AFTER_ERROR: No value passed for parameter 'c'.
// WITH_STDLIB
fun foo(a: Int, b: Int, c: Int) {}

fun test() {
    foo(<caret>)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyRemainingRequiredArgumentsByNameFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyRemainingRequiredArgumentsByNameFix