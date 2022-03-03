// "Change type to mutable" "false"
// TOOL: org.jetbrains.kotlin.idea.inspections.SuspiciousCollectionReassignmentInspection
// ACTION: Replace overloaded operator with function call
// ACTION: Replace with ordinary assignment
// WITH_STDLIB
class Test {
    var list = listOf(1)
    fun test() {
        list +=<caret> 2
    }
}