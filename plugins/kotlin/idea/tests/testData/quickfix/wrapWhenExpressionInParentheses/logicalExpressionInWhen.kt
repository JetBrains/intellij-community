// "Wrap expression in parentheses" "true"
// ERROR: 'when' expression must be exhaustive, add necessary 'true', 'false' branches or 'else' branch instead
// K2_AFTER_ERROR: 'when' expression must be exhaustive. Add the 'true', 'false' branches or an 'else' branch.
interface A {
    operator fun contains(other: A): Boolean
}

fun test(x: A, b: Boolean) {
    when (b) {
        x in x<caret> -> {}
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConfusingExpressionInWhenBranchFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ConfusingBranchConditionErrorFixFactories$WrapExpressionInParenthesesFixFactory