// "Specify all remaining arguments by name" "false"
// WITH_STDLIB
fun foo(a: Int, b: Int): Int = 1

fun test() {
    foo(foo(1, 2<caret>), 1)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyAllRemainingArgumentsByNameFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyAllRemainingArgumentsByNameFix