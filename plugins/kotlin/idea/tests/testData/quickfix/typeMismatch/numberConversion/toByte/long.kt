// "Convert expression to 'Byte'" "true"
// K2_ERROR: Argument type mismatch: actual type is 'Long', but 'Byte' was expected.
fun byte(x: Byte) {}

fun test(l: Long) {
    byte(<caret>l)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix