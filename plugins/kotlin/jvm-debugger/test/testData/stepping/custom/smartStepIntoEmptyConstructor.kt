package smartStepIntoEmptyConstructor

fun genInt() = 42

class A {
    val x = genInt()
}

fun consume(a: A) = Unit

fun testSmartStepIntoConsume() {
    // SMART_STEP_INTO_BY_INDEX: 1
    // RESUME: 1
    //Breakpoint!
    consume(A())
}

fun testSmartStepIntoConstructor() {
    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_OVER: 3
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 1
    //Breakpoint!
    consume(A())
}

fun main() {
    testSmartStepIntoConsume()
    testSmartStepIntoConstructor()
}
