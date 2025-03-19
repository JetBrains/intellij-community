fun foo(a: String): Int {
    if (a == "a") {
        return 0
    } else if (a == "b") {
        return 1
    } else {
        return 2
    }
}

fun main() {
    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_OVER: 1
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 2
    //Breakpoint!
    foo("a") + foo("b") + foo("c")

    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_OVER: 2
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 1
    //Breakpoint!
    foo("a") + foo("b") + foo("c")

    // SMART_STEP_INTO_BY_INDEX: 3
    // STEP_OVER: 3
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 0
    //Breakpoint!
    foo("a") + foo("b") + foo("c")
}
