// "Convert expression to 'Int'" "true"
// WITH_STDLIB
// K2_ERROR: Argument type mismatch: actual type is 'UInt', but 'Int' was expected.
fun foo(param: Int) {}

fun test(expr: UInt) {
    foo(<caret>expr)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix