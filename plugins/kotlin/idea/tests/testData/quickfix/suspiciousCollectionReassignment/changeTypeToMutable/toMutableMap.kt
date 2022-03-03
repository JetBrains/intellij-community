// "Change type to mutable" "true"
// TOOL: org.jetbrains.kotlin.idea.inspections.SuspiciousCollectionReassignmentInspection
// WITH_STDLIB
fun toMutableMap() {
    var map = foo()
    map -=<caret> 3
}

fun foo() = mapOf(1 to 2)