// "Specify all remaining arguments by name" "true"
// WITH_STDLIB
// K2_ERROR: No value passed for parameter 'a'.
fun foo(a: Int, vararg b: Int) {}

fun test() {
    foo(<caret>)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyAllRemainingArgumentsByNameFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyAllRemainingArgumentsByNameFix