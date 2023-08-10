package notCapturedLambdaParameter

data class A(val x: String)

fun foo(a: A, block: (A) -> Unit) = block(a)

fun main() {
    foo(A("O")) { (x) ->
        block {
            // EXPRESSION: x
            // RESULT: 'x' is not captured
            //Breakpoint!
            println()
        }
    }
}

fun block(block: () -> Unit) {
    block()
}