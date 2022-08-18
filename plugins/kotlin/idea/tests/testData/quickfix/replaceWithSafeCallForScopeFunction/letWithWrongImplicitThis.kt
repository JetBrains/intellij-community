// "Replace scope function with safe (?.) call" "false"
// WITH_STDLIB
// ACTION: Add 'return@let'
// ACTION: Add non-null asserted (!!) call
// ACTION: Convert to single-line lambda
// ACTION: Do not show implicit receiver and parameter hints
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Introduce local variable
// ACTION: Move lambda argument into parentheses
// ACTION: Replace with safe (this?.) call
// ACTION: Specify explicit lambda signature
// ERROR: Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type String?

fun String?.foo(a: String?) {
    a.let { s ->
        <caret>length
    }
}