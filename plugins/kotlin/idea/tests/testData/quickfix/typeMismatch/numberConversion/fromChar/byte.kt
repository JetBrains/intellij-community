// "Convert expression to 'Byte'" "true"
// WITH_STDLIB
// K2_ERROR: Argument type mismatch: actual type is 'Char', but 'Byte' was expected.
fun byte(x: Byte) {}

fun test(c: Char) {
    byte(<caret>c)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix