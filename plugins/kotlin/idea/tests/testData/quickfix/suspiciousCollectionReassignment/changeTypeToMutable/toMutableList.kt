// "Change type to mutable" "true"
// TOOL: org.jetbrains.kotlin.idea.inspections.SuspiciousCollectionReassignmentInspection
// WITH_STDLIB
fun test() {
    var list = foo()
    list -=<caret> 2
}

fun foo() = listOf(1)