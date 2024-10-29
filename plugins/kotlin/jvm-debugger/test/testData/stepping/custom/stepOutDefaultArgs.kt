package stepOutDefaultArgs

fun test1(
    x: Int = 0,
    y: Int = 1
) {
    // STEP_OUT: 1
    // RESUME: 1
    //Breakpoint!
    println("$x $y")
}

fun testStepOut() {
    test1()
}

fun test2(
    x: Int = 0,
    y: Int = 1
) {
    // STEP_OVER: 2
    // RESUME: 1
    //Breakpoint!
    println("$x $y")
}

fun testStepOver() {
    test2()
}

fun main() {
    testStepOut()
    testStepOver()
}
