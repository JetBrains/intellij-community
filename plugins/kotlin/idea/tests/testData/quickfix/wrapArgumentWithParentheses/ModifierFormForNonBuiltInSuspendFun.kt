// "Wrap argument with parentheses" "true"
infix fun Int.suspend(bar: () -> Unit) {}

fun foo() {
    1 suspend<caret> fun() {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithParenthesesFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithParenthesesFix