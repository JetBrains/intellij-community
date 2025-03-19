// "Convert expression to 'Int'" "true"
fun test(b: Byte, i: Int) {
    when (i) {
        <caret>b -> {}
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix
// IGNORE_K2