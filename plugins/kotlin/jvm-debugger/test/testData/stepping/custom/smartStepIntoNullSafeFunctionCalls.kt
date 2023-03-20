package smartStepIntoNullSafeFunctionCalls

fun Any.foo(): Any {
    return this
}

fun returnsNotNull(): Any? {
    return Any()
}

fun returnsNull(): Any? {
    return null
}

fun main() {
    // STEP_OVER: 1
    //Breakpoint!
    val p1 = 1

    // SMART_STEP_INTO_BY_INDEX: 1
    // RESUME: 1
    returnsNotNull()?.foo()?.foo()

    // STEP_OVER: 1
    //Breakpoint!
    val p2 = 2

    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    returnsNotNull()?.foo()?.foo()

    // STEP_OVER: 1
    //Breakpoint!
    val p3 = 3

    // SMART_STEP_INTO_BY_INDEX: 3
    // RESUME: 1
    returnsNotNull()?.foo()?.foo()

    // STEP_OVER: 1
    //Breakpoint!
    val p4 = 4

    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_OVER: 2
    returnsNull()?.foo()?.foo()

    // SMART_STEP_INTO_BY_INDEX: 2
    returnsNull()?.foo()?.foo()

    // SMART_STEP_INTO_BY_INDEX: 3
    returnsNull()?.foo()?.foo()
}

// IGNORE_K2