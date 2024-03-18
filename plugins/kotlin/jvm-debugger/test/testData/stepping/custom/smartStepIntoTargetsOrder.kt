package smartStepIntoTargetsOrder

fun main() {
    // SMART_STEP_INTO_BY_INDEX: 1
    // RESUME: 1
    //Breakpoint! (lambdaOrdinal = -1)
    f1({ foo() }, { boo() })

    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint! (lambdaOrdinal = -1)
    f1({ foo() }, { boo() })

    // SMART_STEP_INTO_BY_INDEX: 3
    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint! (lambdaOrdinal = -1)
    f1({ foo() }, { boo() })
}

fun f1(l1: () -> Unit, l2: () -> Unit) {
    l1()
    l2()
}

fun foo() = Unit
fun boo() = Unit

// IGNORE_K2
