// "Convert expression to 'Byte'" "true"
// K2_ERROR: RETURN_TYPE_MISMATCH
fun test(l: Long): Byte {
    return l<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix