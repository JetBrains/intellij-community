// "Replace with safe (?.) call" "true"
// WITH_STDLIB
var i = 0

fun foo(s: String?) {
    i = s<caret>.length
}
