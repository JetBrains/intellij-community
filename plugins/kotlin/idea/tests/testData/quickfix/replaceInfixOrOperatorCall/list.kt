// "Replace with safe (?.) call" "true"
// WITH_STDLIB

fun foo(list: List<String>?) {
    list<caret>[0]
}