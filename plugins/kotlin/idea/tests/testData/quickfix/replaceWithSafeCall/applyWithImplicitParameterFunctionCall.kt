// "Replace with safe (this?.) call" "true"
// WITH_STDLIB
fun foo(a: String?) {
    a.apply {
        <caret>lowercase()
    }
}
// IGNORE_K2
