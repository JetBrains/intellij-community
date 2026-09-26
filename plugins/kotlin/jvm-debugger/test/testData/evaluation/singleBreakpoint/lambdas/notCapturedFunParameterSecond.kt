package notCapturedFunParameterSecond

fun main() {
    val a = arrayOf(1)
    a.map(fun(it) {
        block {
            // EXPRESSION: it
            // RESULT: 'it' is not captured
            //Breakpoint!
            println()
        }
    })

}

fun block(block: () -> Unit) {
    block()
}