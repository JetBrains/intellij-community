// "Replace scope function with safe (?.) call" "true"
// WITH_STDLIB
fun foo(a: String?) {
    a.apply {
        <caret>toLowerCase()
    }
}