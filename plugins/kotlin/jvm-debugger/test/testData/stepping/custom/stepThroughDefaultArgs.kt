package stepThroughDefaultArgs

fun test1(
    x: Int = 0,
    y: Int = 1
) {
    println("$x $y")
}

fun testMultilineFun() {
    // STEP_INTO: 1
    // STEP_OVER: 3
    // RESUME: 1
    //Breakpoint!
    test1()
}

fun test2(x: Int = 0,
    y: Int = 1
) {
    println("$x $y")
}

fun testFirstParamOnThreSameLine() {
    // STEP_INTO: 1
    // STEP_OVER: 2
    // RESUME: 1
    //Breakpoint!
    test2()
}

fun test3(x: Int = 0, y: Int = 1) {
    println("$x $y")
}

fun testSingleLine() {
    // STEP_INTO: 1
    // STEP_OVER: 1
    // RESUME: 1
    //Breakpoint!
    test3()
}

fun foo() = 43

fun test4(
    x: Int = foo(),
    y: Int = foo(),
) {
    println("$x $y")
}

fun testMultilineFunWithMethodCalls() {
    // STEP_INTO: 1
    // STEP_OVER: 3
    // RESUME: 1
    //Breakpoint!
    test4()
}

fun main() {
    testMultilineFun()
    testFirstParamOnThreSameLine()
    testSingleLine()
    testMultilineFunWithMethodCalls()
}
