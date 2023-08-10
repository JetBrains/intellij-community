// "Convert expression to 'Float'" "true"
// WITH_STDLIB
fun float(x: Float) {}

fun test(c: Char) {
    float(<caret>c)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix