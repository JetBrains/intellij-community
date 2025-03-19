// "Surround with null check" "false"
// ACTION: Add non-null asserted (s!!) call
// ACTION: Enable option 'Local variable types' for 'Types' inlay hints
// ACTION: Replace with safe (?.) call
// ERROR: Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type String?

fun foo(s: String?) {
    val x = s<caret>.hashCode()
}