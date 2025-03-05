// "Wrap with '?.let { ... }' call" "false"
// ACTION: Add 's =' to argument
// ACTION: Add non-null asserted (s!!) call
// ACTION: Convert to run
// ACTION: Convert to with
// ACTION: Replace with safe (?.) call
// ACTION: Surround with null check
// DISABLE_ERRORS
// WITH_STDLIB
fun foo(s: String?) {}

fun bar(s: String?) {
    foo(s<caret>.substring(1))
}