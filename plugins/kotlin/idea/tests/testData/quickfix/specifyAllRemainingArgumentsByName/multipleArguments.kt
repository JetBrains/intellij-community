// "Specify all remaining arguments by name" "true"
// WITH_STDLIB
fun foo(a: Int, b: Int, c: Int, d: Int): Int = a + b + c + d

fun test() {
    foo(<caret>)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyAllRemainingArgumentsByNameFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyAllRemainingArgumentsByNameFix