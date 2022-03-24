// "Surround with null check" "true"
// WITH_STDLIB

fun foo(list: List<String>?) {
    for (element in <caret>list) {}
}