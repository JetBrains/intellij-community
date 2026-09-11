// "Simplify comparison" "true"
fun foo(x: Int, arg: Boolean) {
    if (arg && <caret>x != null) {

    }
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.SimplifyExpressionFix