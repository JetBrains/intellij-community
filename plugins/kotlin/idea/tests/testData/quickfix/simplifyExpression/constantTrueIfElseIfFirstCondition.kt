// "Simplify expression" "true"
// TOOL: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.dfa.KotlinConstantConditionsInspection

fun test(a: Int, b: Boolean) {
    if (a <= 5) return
    if (a > 5<caret>) {
        println("a")
    } else if (b) {
        println("b")
    }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.SimplifyExpressionFix