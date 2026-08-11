// "Remove else branch" "true"
// K2_ERROR: NO_VALUE_FOR_PARAMETER

fun foo(b: Boolean) {
    when (b) {
        true -> return
        false -> {}
        <caret>else -> error()
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveWhenBranchFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveWhenBranchFix