package stepThroughDefaultArgs

class Clazz1(
    p0: Int = 10,
    p1: Int = 11,
    p2: Int = 12,
)

fun test1() {
    // STEP_INTO: 1
    // STEP_OVER: 6
    // RESUME: 1
    //Breakpoint!
    Clazz1()
}

class Clazz2(
    // STEP_OVER: 5
    //Breakpoint!
    p0: Int = 10,
    p1: Int = 11,
    p2: Int = 12,
)

fun test2() {
    Clazz2()
}

fun main() {
    test1()
    test2()
}

