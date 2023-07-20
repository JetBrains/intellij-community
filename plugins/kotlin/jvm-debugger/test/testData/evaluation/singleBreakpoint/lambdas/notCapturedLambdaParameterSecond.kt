package notCapturedLambdaParameterSecond
fun main() {
    val (x, y) = 1 to 2
    block {
        // EXPRESSION: x
        // RESULT: 'x' is not captured
        //Breakpoint!
        println()
    }
}
fun block(block: () -> Unit) {
    block()
}