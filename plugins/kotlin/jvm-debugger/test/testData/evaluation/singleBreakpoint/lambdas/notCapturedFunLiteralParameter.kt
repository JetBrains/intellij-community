package notCapturedFunLiteralParameter

fun main() {
    val foo: (Int) -> Unit = { x ->
        block {
            // EXPRESSION: x
            // RESULT: 'x' is not captured
            //Breakpoint!
            println()
        }
    }
    foo(3)
}

fun block(block: () -> Unit) {
    block()
}