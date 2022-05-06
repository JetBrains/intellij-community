// "Replace with assignment (original is empty)" "false"
// ACTION: Do not show return expression hints
// ACTION: Replace overloaded operator with function call
// ACTION: Replace with ordinary assignment
// WITH_STDLIB
fun test(otherList: List<Int>) {
    var list: List<Int>
    list = emptyList<Int>()
    list +=<caret> otherList
}