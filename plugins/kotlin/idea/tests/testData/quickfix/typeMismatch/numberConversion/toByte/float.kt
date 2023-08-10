// "Convert expression to 'Byte'" "true"
fun byte(x: Byte) {}

fun test(f: Float) {
    byte(<caret>f)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix