// "Specify all remaining arguments by name" "true"
// WITH_STDLIB
// K2_ERROR: NONE_APPLICABLE
fun foo(a: Int, b: Int) {}
fun foo(a: String, b: String, c: String) {}
fun foo(a: Int, b: Int, c: Int, d: Int) {}

fun test() {
    foo(a = ""<caret>)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyAllRemainingArgumentsByNameFix