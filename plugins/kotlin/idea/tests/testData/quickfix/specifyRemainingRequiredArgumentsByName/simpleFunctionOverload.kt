// "Specify remaining required arguments by name" "true"
// WITH_STDLIB
fun foo(a: Int, b: String) {}
fun foo(a: Int) {}

fun test() {
    foo<caret>()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyRemainingRequiredArgumentsByNameFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyRemainingRequiredArgumentsByNameFix