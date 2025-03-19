// "Remove else branch" "true"

sealed class Parent

class AChild : Parent()
class BChild : Parent()

fun foo(p: Parent) {
    when (p) {
        is AChild -> return
        is BChild -> {}
        <caret>else -> {}
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveWhenBranchFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveWhenBranchFix