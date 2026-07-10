// "Convert expression to 'Long'" "true"
// WITH_STDLIB
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
fun long(x: Long) {}

fun test(c: Char) {
    long(<caret>'c')
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix