// "Convert expression to 'Short'" "true"
// API_VERSION: 1.2
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
fun short(x: Short) {}

fun test(f: Float) {
    short(<caret>f)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix