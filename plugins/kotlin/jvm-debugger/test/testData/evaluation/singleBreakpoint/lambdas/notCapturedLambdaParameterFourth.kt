package notCapturedLambdaParameterFourth
fun main() {
    App()
}

class App {
    init {
        val map = mapOf(Pair("test", "test"))
        for ((key, value) in map) {
            // do something with the key and the value
            block {
                // EXPRESSION: key
                // RESULT: 'key' is not captured
                //Breakpoint!
                println()
            }
        }
    }
}

fun block(block: () -> Unit) {
    block()
}