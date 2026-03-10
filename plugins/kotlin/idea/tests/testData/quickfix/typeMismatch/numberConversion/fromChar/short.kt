// "Convert expression to 'Short'" "true"
// WITH_STDLIB
// K2_ERROR: Argument type mismatch: actual type is 'Char', but 'Short' was expected.
fun short(x: Short) {}

fun test(c: Char) {
    short(<caret>c)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix