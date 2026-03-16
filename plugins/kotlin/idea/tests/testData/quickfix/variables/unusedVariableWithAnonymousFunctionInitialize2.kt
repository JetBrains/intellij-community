// "Remove variable 'a' (may change semantics)" "true"
fun test() {
    val <caret>a: (String) -> Unit = fun(s: String) { s + s }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemovePsiElementSimpleFix$RemoveVariableFactory$doCreateQuickFix$removePropertyFix$1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased.UnusedVariableInspection$createQuickFix$1