// "Replace with filter" "true"
// TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.SuspiciousCollectionReassignmentInspection
// WITH_STDLIB
fun test() {
    var list = listOf(1, 2, 3)
    list -=<caret> listOf(2)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.SuspiciousCollectionReassignmentInspection$ReplaceWithFilterFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.SuspiciousCollectionReassignmentInspection$ReplaceWithFilterFix