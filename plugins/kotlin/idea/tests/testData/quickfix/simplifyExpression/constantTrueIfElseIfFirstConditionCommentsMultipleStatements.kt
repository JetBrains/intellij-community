// "Simplify expression" "true"
// TOOL: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.dfa.KotlinConstantConditionsInspection

fun test(a: Int, b: Boolean) {
    // 1
    if (a <= 5) return // 2
    if (a > 5<caret>) { // 3
        // 4
        println("a") // 51
        println("c") // 52
    } else if (b) { // 6
        // 7
        println("b") // 8
    } // 9
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.SimplifyExpressionFix