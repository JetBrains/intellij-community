// "Specify all remaining arguments by name" "true"
// WITH_STDLIB
// K2_ERROR: No value passed for parameter 'a'.
// K2_ERROR: No value passed for parameter 'b'.
// K2_ERROR: No value passed for parameter 'c'.
// K2_ERROR: No value passed for parameter 'd'.
fun foo(a: Int, b: Int, c: Int, d: Int): Int = a + b + c + d

fun test() {
    foo(<caret>)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyAllRemainingArgumentsByNameFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyAllRemainingArgumentsByNameFix