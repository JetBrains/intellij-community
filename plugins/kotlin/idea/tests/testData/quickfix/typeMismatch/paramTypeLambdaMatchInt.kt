// "Surround with lambda" "true"
fun int() {
    i(<caret>123)
}

fun i(block: () -> Long) {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithLambdaFix
/* IGNORE_K2 */