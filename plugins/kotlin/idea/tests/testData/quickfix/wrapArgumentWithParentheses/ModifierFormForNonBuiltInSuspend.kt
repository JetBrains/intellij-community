// "Wrap argument with parentheses" "true"
// K2_ERROR: Calls in the form of 'suspend {}' are deprecated because 'suspend' in this context will have the meaning of a modifier. Surround the lambda with parentheses: 'suspend({ ... })'.
infix fun Int.suspend(bar: () -> Unit) {}

fun foo() {
    1 suspend<caret> {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithParenthesesFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithParenthesesFix