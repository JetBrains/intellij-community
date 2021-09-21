// "Remove variable 'a'" "false"
// ACTION: Compiler warning 'UNUSED_VARIABLE'
// ACTION: Split property declaration
fun test() {
    val <caret>a: (String) -> Unit = { s -> s + s }
}