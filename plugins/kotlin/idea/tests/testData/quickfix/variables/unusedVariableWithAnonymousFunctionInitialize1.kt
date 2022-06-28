// "Remove variable 'a'" "false"
// ACTION: Compiler warning 'UNUSED_VARIABLE' options
// ACTION: Do not show return expression hints
// ACTION: Split property declaration
fun test() {
    val <caret>a: (String) -> Unit = fun(s) { s + s }
}