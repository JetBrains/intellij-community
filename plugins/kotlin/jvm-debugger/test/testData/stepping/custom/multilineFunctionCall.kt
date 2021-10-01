package multilineFunctionCall

inline fun inlineFun(): Int {
    //Breakpoint!, lambdaOrdinal = -1
    return listOf(1, 2, 3).map { it * 2 }.sum()
}

inline fun nestedInlineFun(): Int {
    //Breakpoint!
    return inlineFun()
}

fun String.ext(val1: Int, val2: Int, val3: Int) = this

fun main() {
    //Breakpoint!
    "A".ext(
        //Breakpoint!
        inlineFun(),
        //Breakpoint!, lambdaOrdinal = -1
        2.let { it + 1 },
        //Breakpoint!
        nestedInlineFun()
    )
}

// RESUME: 7
