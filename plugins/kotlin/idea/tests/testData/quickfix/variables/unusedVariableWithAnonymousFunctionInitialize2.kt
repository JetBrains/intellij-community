// "Remove variable 'a' (may change semantics)" "true"
fun test() {
    val <caret>a: (String) -> Unit = fun(s: String) { s + s }
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.RemoveUnusedVariableFix