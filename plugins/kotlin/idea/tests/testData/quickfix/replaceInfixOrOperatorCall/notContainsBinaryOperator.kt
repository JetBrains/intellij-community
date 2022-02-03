// "Replace with safe (?.) call" "true"
// WITH_STDLIB

fun foo(bar: String?) {
    "foo" !in<caret> bar
}