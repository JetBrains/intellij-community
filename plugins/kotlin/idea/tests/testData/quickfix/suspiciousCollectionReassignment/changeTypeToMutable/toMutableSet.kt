// "Change type to mutable" "true"
// TOOL: org.jetbrains.kotlin.idea.inspections.SuspiciousCollectionReassignmentInspection
// WITH_STDLIB
fun test() {
    var set = foo()
    set -=<caret> 1
}

fun foo() = setOf(1)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.SuspiciousCollectionReassignmentInspection$ChangeTypeToMutableFix