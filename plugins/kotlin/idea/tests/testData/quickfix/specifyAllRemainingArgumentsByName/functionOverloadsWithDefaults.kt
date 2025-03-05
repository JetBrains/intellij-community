// "Specify all remaining arguments by name" "true"
// WITH_STDLIB
fun foo(a: Int, b: String) {}
fun foo(a: Int, b: String, c: Int) {}
fun foo(a: Int, b: String = "", c: Int = 5, d: Int = 5) {}

fun test() {
    foo<caret>()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyAllRemainingArgumentsByNameFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyAllRemainingArgumentsByNameFix