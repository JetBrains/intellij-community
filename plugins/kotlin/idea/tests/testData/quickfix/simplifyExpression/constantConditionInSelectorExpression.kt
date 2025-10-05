// "Simplify expression" "true"
// TOOL: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.dfa.KotlinConstantConditionsInspection
fun main() {
    data class Foo(val i: Int? = null)
    val foo = Foo()
    val bar = foo.i?.let {
        if (0 =<caret>= 0) { "test" }
    } ?: ""
    print(bar)
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.SimplifyExpressionFix