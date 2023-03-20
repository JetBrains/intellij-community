package smartStepIntoInlineLambdasOnSameLine

fun foo1() = 1
fun foo2() = 2
fun foo3() = 3

fun main() {
    // STEP_OVER: 1
    //Breakpoint!
    val x1 = 1

    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_INTO: 1
    // RESUME: 1
    x1.let { foo1() }.let { foo2() }.let { foo3() }

    // STEP_OVER: 1
    //Breakpoint!
    val x2 = 2

    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_INTO: 1
    // RESUME: 1
    x2.let { foo1() }.let { foo2() }.let { foo3() }

    // STEP_OVER: 1
    //Breakpoint!
    val x3 = 3

    // SMART_STEP_INTO_BY_INDEX: 3
    // STEP_INTO: 1
    // RESUME: 1
    x3.let { foo1() }.let { foo2() }.let { foo3() }

    // STEP_OVER: 1
    //Breakpoint!
    val x4 = 4

    // SMART_STEP_INTO_BY_INDEX: 1
    // RESUME: 1
    x4
        .let { foo1() }
        .let { foo2() }
        .let { foo3() }

    // STEP_OVER: 1
    //Breakpoint!
    val x5 = 5

    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    x5
        .let { foo1() }
        .let { foo2() }
        .let { foo3() }

    // STEP_OVER: 1
    //Breakpoint!
    val x6 = 6

    // SMART_STEP_INTO_BY_INDEX: 3
    // RESUME: 1
    x6
        .let { foo1() }
        .let { foo2() }
        .let { foo3() }
}

// IGNORE_K2
