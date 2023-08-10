// "Remove else branch" "true"

fun foo(b: Boolean) {
    when (b) {
        true -> return
        false -> {}
        <caret>else -> error()
    }
}
/* IGNORE_FIR */

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveWhenBranchFix