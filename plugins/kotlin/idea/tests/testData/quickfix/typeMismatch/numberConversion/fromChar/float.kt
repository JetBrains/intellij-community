// "Convert expression to 'Float'" "true"
// WITH_STDLIB
// K2_ERROR: Argument type mismatch: actual type is 'Char', but 'Float' was expected.
fun float(x: Float) {}

fun test(c: Char) {
    float(<caret>c)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix