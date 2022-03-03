// "Replace with safe (?.) call" "true"
// WITH_STDLIB

fun foo(bar: Int?) {
    bar +<caret> 1
}