// "Replace with safe (?.) call" "true"
// WITH_STDLIB

fun foo(bar: Int?) {
    var i: Int = 1
    i = bar +<caret> 1
}