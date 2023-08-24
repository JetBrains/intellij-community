// "Convert expression to 'Byte'" "true"
fun byte(x: Byte) {}

fun test(d: Double) {
    byte(<caret>d)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix