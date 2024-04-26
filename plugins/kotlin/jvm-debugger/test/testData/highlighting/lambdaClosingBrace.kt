package lambdaClosingBrace

fun test1() = run {
    // STEP_OVER: 1
    // RESUME: 1
    //Breakpoint!
    println()
}

fun test2() = run {
    listOf(1).onEach {
        // STEP_OVER: 1
        // RESUME: 1
        //Breakpoint!
        println(it)
    }.map { it }
}

fun test3() = run {
    // STEP_OVER: 1
    // RESUME: 1
    //Breakpoint!
    println()
 }

fun main() {
    test1()
    test2()
    test3()
}
