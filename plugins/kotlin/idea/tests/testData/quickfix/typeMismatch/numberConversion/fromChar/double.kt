// "Convert expression to 'Double'" "true"
// WITH_STDLIB
// K2_ERROR: Argument type mismatch: actual type is 'Char', but 'Double' was expected.
fun double(x: Double) {}

fun test(c: Char) {
    double(<caret>c)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix