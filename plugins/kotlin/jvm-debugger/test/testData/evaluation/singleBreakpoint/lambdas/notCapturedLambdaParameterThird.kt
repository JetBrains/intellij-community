package notCapturedLambdaParameterThird
fun main() {
    App()
}

class App {
    init {
        block {
            val (x, y) = 1 to 2 // x inside KtNamedFunction
            block {
                // EXPRESSION: x
                // RESULT: 'x' is not captured
                //Breakpoint!
                println()
            }
        }
    }
}

fun block(block: () -> Unit) {
    block()
}