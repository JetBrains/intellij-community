// "Change type to mutable" "true"
// TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.SuspiciousCollectionReassignmentInspection
// WITH_STDLIB
fun test() {
    var list = foo()
    list -=<caret> 2
}

fun foo() = listOf(1)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.SuspiciousCollectionReassignmentInspection$ChangeTypeToMutableFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.SuspiciousCollectionReassignmentInspection$ChangeTypeToMutableFix