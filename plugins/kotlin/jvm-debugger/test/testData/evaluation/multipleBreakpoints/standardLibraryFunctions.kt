package standardLibraryFunctions

fun main() {
    val list = listOf(1, 2, 3)
    list
        //Breakpoint! (lambdaOrdinal = 1)
        .map { x -> x / 2 }
        //Breakpoint! (lambdaOrdinal = 1)
        .filter { x -> x > 1 }
}

// EXPRESSION: x
// RESULT: 1: I
// EXPRESSION: x
// RESULT: 2: I
// EXPRESSION: x
// RESULT: 3: I

// EXPRESSION: x
// RESULT: 0: I
// EXPRESSION: x
// RESULT: 1: I
// EXPRESSION: x
// RESULT: 1: I

// PRINT_FRAME
// SHOW_KOTLIN_VARIABLES
