// "Convert expression to 'Float'" "true"
// WITH_STDLIB
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
fun float(x: Float) {}

fun test(c: Char) {
    float(<caret>c)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix