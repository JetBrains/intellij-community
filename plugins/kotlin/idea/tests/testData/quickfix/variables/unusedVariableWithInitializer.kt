// "Remove variable 'a'" "true"
var cnt = 5
fun getCnt() = cnt++
fun f() {
    var <caret>a = getCnt()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemovePsiElementSimpleFix$RemoveVariableFactory$doCreateQuickFix$removePropertyFix$1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased.UnusedVariableInspection$createQuickFix$1