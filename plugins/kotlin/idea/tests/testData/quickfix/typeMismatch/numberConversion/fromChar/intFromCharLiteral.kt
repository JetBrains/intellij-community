// "Convert expression to 'Int'" "true"
// WITH_STDLIB
fun int(x: Int) {}

fun test(c: Char) {
    int(<caret>'c')
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix