package notCapturedClassParameter

fun main() {
    Test.App("")
}

class Test {
    class App(name: String) {
        init {
            block {
                // EXPRESSION: name
                // RESULT: 'name' is not captured
                //Breakpoint!
                println()
            }
        }
    }
}

fun block(block: () -> Unit) {
    block()
}