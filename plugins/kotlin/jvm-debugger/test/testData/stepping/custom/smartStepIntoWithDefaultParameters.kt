package filterSmartStepWithInlineClass

fun foo(i: Int = 1) {
    bar(i) + bar(i)
}
fun bar(i: Int): Int = i

fun test1() {
    // STEP_INTO: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 0
    //Breakpoint!
    foo()

    // STEP_INTO: 2
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 2
    //Breakpoint!
    foo()
}

fun foo2(i: Int = 1) = bar(i) + bar(i)

fun test2() {
    // STEP_INTO: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 0
    //Breakpoint!
    foo2()

    // STEP_INTO: 2
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 2
    //Breakpoint!
    foo2()
}

fun main() {
    test1()
    test2()
}
