// "Change to 'val'" "true"
fun foo(p: Int) {
    <caret>var (v1, v2) = getPair()!!
    v1
}

fun getPair(): Pair<Int, String>? = null

data class Pair<T1, T2>(val a: T1, val b: T2)

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased.CanBeValInspection$createQuickFixes$1