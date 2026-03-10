// "Convert expression to 'Short'" "true"
// K2_ERROR: Argument type mismatch: actual type is 'Float', but 'Short' was expected.
fun short(x: Short) {}

fun test(f: Float) {
    short(<caret>f)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix