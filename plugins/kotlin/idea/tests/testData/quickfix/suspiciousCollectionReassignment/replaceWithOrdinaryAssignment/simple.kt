// "Replace with ordinary assignment" "true"
// TOOL: org.jetbrains.kotlin.idea.inspections.SuspiciousCollectionReassignmentInspection
// ACTION: Change type to mutable
// ACTION: Remove braces from 'if' statement
// ACTION: Replace overloaded operator with function call
// ACTION: Replace with ordinary assignment
// WITH_STDLIB
fun test(otherList: List<Int>, flag: Boolean) {
    var list = otherList
    if (flag) {
        list <caret>+= 4
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.ReplaceWithOrdinaryAssignmentIntention