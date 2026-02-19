// "Remove variable 'test' (may change semantics)" "true"
fun f() {
    val <caret>test: Int
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemovePsiElementSimpleFix$RemoveVariableFactory$doCreateQuickFix$removePropertyFix$1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased.UnusedVariableInspection$createQuickFix$1