// "Simplify expression" "true"
// TOOL: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.dfa.KotlinConstantConditionsInspection

fun alwaysNull(i: Int) {
    val zeroOrNull = if (i != 15) return
    if (i < 0<caret>) {
        println("Always false")
    }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.SimplifyExpressionFix