// "Simplify comparison" "true"
// TOOL: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.dfa.KotlinConstantConditionsInspection
// Issue: KTIJ-32803

fun test() {
    // returnNotNull() should be called after the simplification, but currently it is not
    if (returnNotNull() != null<caret>) {
        println("Always true")
    }
}

fun returnNotNull(): Int {
    println("Side effect")
    return 1
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.SimplifyExpressionFix