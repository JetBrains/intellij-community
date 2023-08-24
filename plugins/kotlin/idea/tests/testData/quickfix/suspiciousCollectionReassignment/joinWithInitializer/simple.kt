// "Join with initializer" "true"
// TOOL: org.jetbrains.kotlin.idea.inspections.SuspiciousCollectionReassignmentInspection
// WITH_STDLIB
fun test(otherList: List<Int>) {
    var list = createList()
    // comment
    list <caret>+= otherList
}

fun createList(): List<Int> = listOf(1, 2, 3)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.SuspiciousCollectionReassignmentInspection$JoinWithInitializerFix