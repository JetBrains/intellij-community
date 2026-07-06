// "Change to '1u'" "true"
// WITH_STDLIB
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
fun foo(param: UInt) {}

fun test() {
    foo(<caret>1)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrongPrimitiveLiteralFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.WrongPrimitiveLiteralFix