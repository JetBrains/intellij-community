// "Replace with safe (?.) call" "true"
// WITH_STDLIB
fun foo(s: String?) {
    1 + s<caret>.length
}
