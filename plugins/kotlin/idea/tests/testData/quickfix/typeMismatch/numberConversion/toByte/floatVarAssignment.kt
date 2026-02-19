// "Convert expression to 'Byte'" "true"
fun test(f: Float) {
    var x: Byte
    x = f<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix