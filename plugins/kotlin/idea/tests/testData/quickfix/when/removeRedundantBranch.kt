// "Remove branch" "true"
fun test(x: Int): String {
    return when (x) {
        1 -> "1"
        2 -> "2"
        <caret>null -> "null"
        else -> ""
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveWhenBranchFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveWhenBranchFix