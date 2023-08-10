// "Convert expression to 'Int'" "true"
// WITH_STDLIB
fun foo(param: Int) {}

fun test(expr: UInt) {
    foo(<caret>expr)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix