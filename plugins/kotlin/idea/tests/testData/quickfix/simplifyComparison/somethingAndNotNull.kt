// "Simplify comparison" "true"
fun foo(x: Int, arg: Boolean) {
    if (arg && <caret>x != null) {

    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SimplifyComparisonFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SimplifyComparisonFixFactory$SimplifyComparisonFix