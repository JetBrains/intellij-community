// "Change to '1'" "true"
// WITH_STDLIB
// K2_ERROR: Argument type mismatch: actual type is 'UInt', but 'Int' was expected.
fun foo(param: Int) {}

fun test() {
    foo(<caret>1u)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrongPrimitiveLiteralFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.WrongPrimitiveLiteralFix