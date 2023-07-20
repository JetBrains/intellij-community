package stepOutOfInlineLambdas

fun foo1() = 1
fun foo2() = 2
fun foo3() = 3

fun main() {
    // STEP_OVER: 1
    //Breakpoint!
    val x1 = 1

    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_INTO: 1
    // STEP_OUT: 2
    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_INTO: 1
    // STEP_OUT: 2
    // SMART_STEP_INTO_BY_INDEX: 3
    // STEP_INTO: 1
    // RESUME: 1
    x1.let { foo1(); println() }.let { foo2(); println() }.let { foo3() }

    // STEP_OVER: 1
    //Breakpoint!
    val x2 = 2

    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_OUT: 1
    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_OUT: 1
    x2.let {
        it + 1
    }.let {
        it + 2
    }
}

// IGNORE_K2
