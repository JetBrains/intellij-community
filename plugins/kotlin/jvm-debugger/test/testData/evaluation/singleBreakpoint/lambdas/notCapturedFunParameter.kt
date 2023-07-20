package notCapturedFunParameter

fun main() {
    foo("hello")
}

fun foo(a: String) {
    block {
        // EXPRESSION: a
        // RESULT: 'a' is not captured
        //Breakpoint!
        println()
    }
}

fun block(block: () -> Unit) {
    block()
}
