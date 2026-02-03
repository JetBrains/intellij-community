// "Simplify expression" "true"
// TOOL: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.dfa.KotlinConstantConditionsInspection
// IGNORE_K2
// Issue: KTIJ-32804

fun test(a: Int, b: Boolean) {
    // 1
    if (a <= 5) return // 2
    if (a > 5<caret>) { // 3
        // 4
        println("a") // 5
    } else if (b) { // 6
        // 7
        println("b") // 8
    } // 9
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.SimplifyExpressionFix