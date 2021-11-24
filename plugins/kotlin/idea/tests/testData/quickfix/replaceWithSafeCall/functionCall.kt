// "Replace with safe (?.) call" "true"
// WITH_STDLIB
fun foo(a: String?) {
    a<caret>.toLowerCase()
}