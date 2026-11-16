// NEW_NAME: value
fun f(list: List<Int>) {
    list.map { x<caret> -> x + 1 }
}
