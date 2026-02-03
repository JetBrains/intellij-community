// "Convert expression to 'Int'" "true"
// WITH_STDLIB
fun foo() {
    val l: Int
    l = "1".toLong()<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix