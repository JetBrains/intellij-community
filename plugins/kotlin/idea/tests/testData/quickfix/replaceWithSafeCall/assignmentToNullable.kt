// "Replace with safe (?.) call" "true"
// WITH_STDLIB
var i: Int? = 0

fun foo(s: String?) {
    i = s<caret>.length
}
