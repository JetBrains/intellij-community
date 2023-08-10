// "Wrap expression in parentheses" "true"
// ERROR: 'when' expression must be exhaustive, add necessary 'true', 'false' branches or 'else' branch instead
interface A {
    operator fun contains(other: A): Boolean
}

fun test(x: A, b: Boolean) {
    when (b) {
        x in x<caret> -> {}
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConfusingExpressionInWhenBranchFix