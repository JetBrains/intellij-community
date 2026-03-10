// "Remove else branch" "true"
// K2_ERROR: No value passed for parameter 'message'.

fun foo(b: Boolean) {
    when (b) {
        true -> return
        false -> {}
        <caret>else -> error()
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveWhenBranchFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveWhenBranchFix