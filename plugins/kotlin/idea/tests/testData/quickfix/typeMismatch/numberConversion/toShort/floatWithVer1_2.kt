// "Convert expression to 'Short'" "true"
// API_VERSION: 1.2
fun short(x: Short) {}

fun test(f: Float) {
    short(<caret>f)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix