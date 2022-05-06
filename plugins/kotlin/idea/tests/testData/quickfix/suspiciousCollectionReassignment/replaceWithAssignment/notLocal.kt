// "Replace with assignment (original is empty)" "false"
// ACTION: Do not show return expression hints
// ACTION: Replace overloaded operator with function call
// ACTION: Replace with ordinary assignment
// WITH_STDLIB
class Test {
    var list = emptyList<Int>()
    fun test(otherList: List<Int>) {
        list +=<caret> otherList
    }
}
