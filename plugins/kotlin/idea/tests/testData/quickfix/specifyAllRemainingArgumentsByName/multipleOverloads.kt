// "Specify all remaining arguments by name" "true"
// WITH_STDLIB
// K2_ERROR: NONE_APPLICABLE
// K2_ERROR: NO_VALUE_FOR_PARAMETER
// K2_ERROR: NO_VALUE_FOR_PARAMETER
fun foo(a: Int, b: Int) {}
fun foo(a: String, b: String, c: String) {}

fun test() {
    foo(<caret>)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyAllRemainingArgumentsByNameFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyAllRemainingArgumentsByNameFix