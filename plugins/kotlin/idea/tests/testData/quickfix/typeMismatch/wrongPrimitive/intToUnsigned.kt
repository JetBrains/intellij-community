// "Change to '1u'" "true"
// WITH_STDLIB
fun foo(param: UInt) {}

fun test() {
    foo(<caret>1)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrongPrimitiveLiteralFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.WrongPrimitiveLiteralFix