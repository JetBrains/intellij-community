// "Replace with safe (this?.) call" "true"
// WITH_STDLIB
var i = 0

fun foo(a: String?) {
    a.run {
        i = <caret>length ?: 0
    }
}
