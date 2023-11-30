// "Convert expression to 'Byte'" "true"
// WITH_STDLIB
fun byte(x: Byte) {}

fun test(c: Char) {
    byte(<caret>c)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix