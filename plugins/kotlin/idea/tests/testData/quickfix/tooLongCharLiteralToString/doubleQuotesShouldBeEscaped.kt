// "Convert too long character literal to string" "true"
// K2_ERROR: Too many characters in a character literal.

fun foo() {
    'foo"bar'<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.TooLongCharLiteralToStringFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.TooLongCharLiteralToStringFix