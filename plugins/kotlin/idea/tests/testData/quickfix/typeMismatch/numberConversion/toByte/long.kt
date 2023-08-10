// "Convert expression to 'Byte'" "true"
fun byte(x: Byte) {}

fun test(l: Long) {
    byte(<caret>l)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix