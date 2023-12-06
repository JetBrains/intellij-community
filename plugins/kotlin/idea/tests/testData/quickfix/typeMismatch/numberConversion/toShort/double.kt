// "Convert expression to 'Short'" "true"
fun short(x: Short) {}

fun test(d: Double) {
    short(<caret>d)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix