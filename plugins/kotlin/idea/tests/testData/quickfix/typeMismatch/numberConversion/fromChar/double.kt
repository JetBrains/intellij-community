// "Convert expression to 'Double'" "true"
// WITH_STDLIB
fun double(x: Double) {}

fun test(c: Char) {
    double(<caret>c)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix