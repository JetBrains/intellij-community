// "Simplify expression" "true"
// TOOL: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.dfa.KotlinConstantConditionsInspection

fun test(a: Int, b: Boolean, c: Boolean) {
    if (a <= 5) return
    // 1
    if (b) { // 2
        println("b") // 3
    } else if (a > 5<caret>) { // 4
        // 5
        println("a") // 6
    } else if (c) {
        println("c")
    }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.SimplifyExpressionFix