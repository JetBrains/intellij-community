// "Wrap with '?.let { ... }' call" "false"
// WITH_STDLIB
// ACTION: Add non-null asserted (!!) call
// ACTION: Convert to run
// ACTION: Convert to with
// ACTION: Replace with safe (?.) call
// ERROR: Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type Int?

fun Int.bar() {}

class My(var x: Int?) {

    fun foo() {
        x<caret>.bar()
    }
}