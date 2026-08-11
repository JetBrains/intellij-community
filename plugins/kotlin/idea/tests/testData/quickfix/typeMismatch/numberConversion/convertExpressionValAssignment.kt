// "Convert expression to 'Int'" "true"
// WITH_STDLIB
// K2_ERROR: ASSIGNMENT_TYPE_MISMATCH
fun foo() {
    val l: Int
    l = "1".toLong()<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix