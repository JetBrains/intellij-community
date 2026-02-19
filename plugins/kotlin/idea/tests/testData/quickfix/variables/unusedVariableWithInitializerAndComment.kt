// "Remove variable 'a' (may change semantics)" "true"

var cnt = 5
fun getCnt() = cnt++
fun f() {
    var <caret>a = getCnt() // comment
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemovePsiElementSimpleFix$RemoveVariableFactory$doCreateQuickFix$removePropertyFix$1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased.UnusedVariableInspection$createQuickFix$1