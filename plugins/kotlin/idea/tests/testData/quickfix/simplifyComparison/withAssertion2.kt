// "Simplify comparison" "true"
// WITH_STDLIB
fun test() {
    val s = ""
    assert(<caret>s != null && true)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SimplifyComparisonFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SimplifyComparisonFixFactory$SimplifyComparisonFix