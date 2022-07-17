// "Change type to mutable" "true"
// TOOL: org.jetbrains.kotlin.idea.inspections.SuspiciousCollectionReassignmentInspection
// WITH_STDLIB
fun test() {
    var list: List<Int> = listOf(1)
    list +=<caret> 2
}