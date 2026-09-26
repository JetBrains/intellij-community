// "Change parameter 'predicate' type of function 'filter' to '(Int) -> Int'" "false"
// ACTION: Add 'return@filter'
// ACTION: Convert to multi-line lambda
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Introduce local variable
// ACTION: Move lambda argument into parentheses
// ACTION: Specify explicit lambda signature
// DISABLE_ERRORS
// WITH_STDLIB
fun test(list: List<Int>) {
    list.filter { 1<caret> }
}