// "Replace with assignment (original is empty)" "true"
// TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.SuspiciousCollectionReassignmentInspection
// WITH_STDLIB
fun test(otherList: List<Int>) {
    var list = listOf<Int>()
    foo()
    bar()
    list <caret>+= otherList
}

fun foo() {}
fun bar() {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.SuspiciousCollectionReassignmentInspection$ReplaceWithAssignmentFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.SuspiciousCollectionReassignmentInspection$ReplaceWithAssignmentFix