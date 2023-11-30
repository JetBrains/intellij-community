// "Convert expression to 'UInt'" "true"
// WITH_STDLIB
fun foo(param: UInt) {}

fun test(expr: Int) {
    foo(<caret>expr)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix