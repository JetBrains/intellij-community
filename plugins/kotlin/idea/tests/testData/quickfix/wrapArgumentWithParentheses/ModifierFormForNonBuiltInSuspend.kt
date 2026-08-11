// "Wrap argument with parentheses" "true"
// K2_ERROR: MODIFIER_FORM_FOR_NON_BUILT_IN_SUSPEND
infix fun Int.suspend(bar: () -> Unit) {}

fun foo() {
    1 suspend<caret> {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithParenthesesFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithParenthesesFix