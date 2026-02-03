fun foo(a: String): Int {
    if (a == "a") {
        return 0
    } else {
        return 1
    }
}

fun main() {
    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_OVER: 1
    // RESUME: 1
    //Breakpoint!
    foo("a") + foo("b")

    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_OVER: 1
    // RESUME: 1
    //Breakpoint!
    foo("a") + foo("b")
}
