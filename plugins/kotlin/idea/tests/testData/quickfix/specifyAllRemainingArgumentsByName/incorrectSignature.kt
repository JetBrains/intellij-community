// "Specify all remaining arguments by name" "false"
// WITH_STDLIB
fun foo(a: Int) {}
fun foo(a: String, b: String, c: String) {}
fun foo(a: String, b: String, c: String, d: String) {}

fun test() {
    foo(a = 5<caret>)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyAllRemainingArgumentsByNameFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyAllRemainingArgumentsByNameFix