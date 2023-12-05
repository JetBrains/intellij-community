// "Change to '1'" "true"
// WITH_STDLIB
fun foo(param: Int) {}

fun test() {
    foo(<caret>1u)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrongPrimitiveLiteralFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.WrongPrimitiveLiteralFix