// "Simplify comparison" "true"
fun foo(x: Int) {
    if (<caret>x != null) {
        bar()
    }
}

fun bar() {}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.SimplifyExpressionFix