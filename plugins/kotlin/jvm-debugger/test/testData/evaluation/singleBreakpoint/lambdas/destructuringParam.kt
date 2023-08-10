package destructuringParam
data class A(val x: String, val y: String)

fun foo(a: A, block: (A) -> String): String = block(a)

fun main(args: Array<String>) {
    //Breakpoint! (lambdaOrdinal = 1)
    foo(A("O", "K")) { (x, y) -> x + y }
}

// PRINT_FRAME

// EXPRESSION: x
// RESULT: "O": Ljava/lang/String;

// EXPRESSION: y
// RESULT: "K": Ljava/lang/String;

// IGNORED on IR backend: the line numbers in the invoke method of the lambda are
// inserted on _entry_ to the method, before destructuring, so lambda breakpoints
// do not show the destructuring variables -- they haven't been initialized yet!