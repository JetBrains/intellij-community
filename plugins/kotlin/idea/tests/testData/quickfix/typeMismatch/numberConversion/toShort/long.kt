// "Convert expression to 'Short'" "true"
fun short(x: Short) {}

fun test(l: Long) {
    short(<caret>l)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix