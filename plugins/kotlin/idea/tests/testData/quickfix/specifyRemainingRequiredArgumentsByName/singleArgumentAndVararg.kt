// "Specify remaining required arguments by name" "false"
// ERROR: No value passed for parameter 'a'
// WITH_STDLIB
fun foo(a: Int, vararg b: Int) {}

fun test() {
    foo(<caret>)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyRemainingRequiredArgumentsByNameFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyRemainingRequiredArgumentsByNameFix