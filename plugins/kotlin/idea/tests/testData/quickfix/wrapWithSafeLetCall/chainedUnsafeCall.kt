// "Wrap with '?.let { ... }' call" "false"
// WITH_STDLIB
// ACTION: Add non-null asserted (!!) call
// ACTION: Introduce local variable
// ACTION: Put calls on separate lines
// ACTION: Replace with safe (?.) call
// ERROR: Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type Int?

fun foo(arg: Int?) {
    arg?.hashCode()<caret>.toLong()
}