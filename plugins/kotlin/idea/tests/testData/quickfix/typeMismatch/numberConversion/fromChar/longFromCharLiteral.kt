// "Convert expression to 'Long'" "true"
// WITH_STDLIB
// K2_ERROR: Argument type mismatch: actual type is 'Char', but 'Long' was expected.
fun long(x: Long) {}

fun test(c: Char) {
    long(<caret>'c')
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix