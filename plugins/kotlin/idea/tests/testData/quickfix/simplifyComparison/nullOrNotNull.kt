// "Simplify comparison" "true"
fun foo(x: Int) {
    if (<caret>x != null || x == null) {

    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.SimplifyComparisonFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.SimplifyComparisonFix