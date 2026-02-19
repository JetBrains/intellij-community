// "Simplify expression" "true"
// TOOL: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.dfa.KotlinConstantConditionsInspection

fun test(lst: List<String>) {
    if (!lst.isEmpty()) return
    println(lst.size<caret>)
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.SimplifyExpressionFix
