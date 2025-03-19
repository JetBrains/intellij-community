// "Simplify comparison" "true"
// WITH_STDLIB
fun test() {
    val s = ""
    assert(<caret>s != null && true)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.SimplifyExpressionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.SimplifyExpressionFix