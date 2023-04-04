package smartStepIntoLabeledLambda

fun foo1() = 1
fun foo2() = 2
fun foo3() = 3

fun main() {
    // STEP_OVER: 1
    //Breakpoint!
    val x1 = 1

    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    foo lam@{
        println()
    }

    // STEP_OVER: 1
    //Breakpoint!
    val x2 = 2

    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    foo lam@{ println() }

    // STEP_OVER: 1
    //Breakpoint!
    val x3 = 3

    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_INTO: 1
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_INTO: 1
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 3
    // STEP_INTO: 1
    // RESUME: 1
    1.let lam1@{ foo1() }.let lam2@{ foo2() }.let lam3@{ foo3() }

    // STEP_OVER: 1
    //Breakpoint!
    val x4 = 4

    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_INTO: 1
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_INTO: 1
    // STEP_OVER: 1
    // STEP_INTO: 1
    1.let lam1@{ foo1() }
        .let lam2@{ foo2() }
        .let lam3@{ foo3() }
}

fun foo(l1: (Int) -> Unit) {
    l1(0)
}

// IGNORE_K2
