// "Convert expression to 'Byte'" "true"
fun test(b: Byte, i: Int) {
    when (b) {
        <caret>i -> {}
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix
// IGNORE_K2