// "Replace with safe (?.) call" "false"
// WITH_STDLIB
// ACTION: Add non-null asserted (!!) call
// ACTION: Replace overloaded operator with function call
// ACTION: Replace with ordinary assignment
// ERROR: Operator call corresponds to a dot-qualified call 'map[3].timesAssign(5)' which is not allowed on a nullable receiver 'map[3]'.
fun test(map: MutableMap<Int, Int>) {
    map[3] *=<caret> 5
}
