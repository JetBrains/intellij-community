// "Convert expression to 'Short'" "true"
// K2_ERROR: Argument type mismatch: actual type is 'Double', but 'Short' was expected.
fun short(x: Short) {}

fun test(d: Double) {
    short(<caret>d)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix