// "Wrap argument with parentheses" "true"
// K2_ERROR: Calls in the form of 'suspend fun' are deprecated because 'suspend' in the context will have the meaning of a modifier. Surround the argument of the call with parentheses: 'suspend(fun() { ... })'. See https://youtrack.jetbrains.com/issue/KT-49264
infix fun Int.suspend(bar: () -> Unit) {}

fun foo() {
    1 suspend<caret> fun() {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithParenthesesFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithParenthesesFix