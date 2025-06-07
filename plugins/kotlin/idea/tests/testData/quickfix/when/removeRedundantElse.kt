// "Remove else branch" "true"

fun foo(b: Boolean) {
    when (b) {
        true -> return
        false -> {}
        <caret>else -> error()
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveWhenBranchFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveWhenBranchFix