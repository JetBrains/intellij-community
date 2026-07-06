// "Specify remaining required arguments by name" "false"
// ERROR: No value passed for parameter 'a'
// WITH_STDLIB
// K2_AFTER_ERROR: NO_VALUE_FOR_PARAMETER
// K2_ERROR: NO_VALUE_FOR_PARAMETER
fun foo(a: Int, vararg b: Int) {}

fun test() {
    foo(<caret>)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyRemainingRequiredArgumentsByNameFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyRemainingRequiredArgumentsByNameFix