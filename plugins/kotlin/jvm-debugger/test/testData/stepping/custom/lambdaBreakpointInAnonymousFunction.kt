package lambdaBreakpointInAnonymousFunction

fun main() {
    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!, lambdaOrdinal = 1
    lambda("first", fun(it: String) { lambda("second", fun(it: String) { foo() }) })

    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!, lambdaOrdinal = 2
    lambda("first", fun(it: String) { lambda("second", fun(it: String) { foo() }) })

    lambda("first", fun(it: String) {
        // RESUME: 1
        //Breakpoint!
        lambda("second", fun(it: String) {
            println()
        })
    })


    lambda("first", fun(it: String) {
        lambda("second", fun(it: String) {
            // RESUME: 1
            //Breakpoint!
            println()
        })
    })
}

fun lambda(s: String, l1: (String) -> Unit) {
    l1(s)
}

fun foo() = Unit
