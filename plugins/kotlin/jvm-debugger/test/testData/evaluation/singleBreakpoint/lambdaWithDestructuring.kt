package lambdaWithDestructuring

// SHOW_KOTLIN_VARIABLES
// PRINT_FRAME

data class C(val x: String, val y: Int)

fun fooC(a: C, block: (C) -> Unit) = block(a)

fun main() {
    fooC(C("Hello", 32)) { (x, y) ->
        // EXPRESSION: x
        // RESULT: "Hello": Ljava/lang/String;
        // EXPRESSION: y
        // RESULT: 32: I
        //Breakpoint!
        println()
    }
}
