package smartStepIntoOnLambdaBreakpoint

fun main() {
    // SMART_STEP_INTO_BY_INDEX: 1
    //Breakpoint! (lambdaOrdinal = 1)
    lambda("first") { lambda("second") { foo() } }

    // RESUME: 1
    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_INTO: 1
    //Breakpoint! (lambdaOrdinal = 1)
    lambda("first") { lambda("second") { foo() } }
}

fun foo() = Unit

fun <T> lambda(obj: T, f: T.() -> Unit) {
    println("lambda: $obj")
    f(obj)
}

// IGNORE_K2
