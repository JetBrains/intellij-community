package smartStepIntoMultiplyInlinedLambdas

fun foo1() = 1
fun foo2() = 2
fun foo3() = 3

inline fun foo() {
    1.let { foo1() }.let { foo2() }.let { foo3() }
}

fun main() {
    // STEP_INTO: 1
    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_INTO: 1
    // STEP_OUT: 1
    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_INTO: 1
    // STEP_OUT: 1
    // SMART_STEP_INTO_BY_INDEX: 3
    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!
    foo()
    // STEP_INTO: 1
    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_INTO: 1
    // STEP_OUT: 1
    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_INTO: 1
    // STEP_OUT: 1
    // SMART_STEP_INTO_BY_INDEX: 3
    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!
    foo()
    // STEP_INTO: 1
    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_INTO: 1
    // STEP_OUT: 1
    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_INTO: 1
    // STEP_OUT: 1
    // SMART_STEP_INTO_BY_INDEX: 3
    // STEP_INTO: 1
    //Breakpoint!
    foo()
}

// IGNORE_K2
