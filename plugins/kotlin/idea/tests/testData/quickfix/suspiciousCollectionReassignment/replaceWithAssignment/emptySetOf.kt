// "Replace with assignment (original is empty)" "true"
// TOOL: org.jetbrains.kotlin.idea.inspections.SuspiciousCollectionReassignmentInspection
// WITH_STDLIB
fun test(otherList: Set<Int>) {
    var list = setOf<Int>()
    foo()
    bar()
    list <caret>+= otherList
}

fun foo() {}
fun bar() {}