// "Convert too long character literal to string" "true"

fun foo() {
    'foo\"bar'<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.TooLongCharLiteralToStringFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.TooLongCharLiteralToStringFix