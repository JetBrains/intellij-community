package stepOverInvokeMethodInLambda

// SKIP_SYNTHETIC_METHODS: true

fun Any.acceptLambda(f: () -> Unit): Any {
    f()
    return this
}

fun main() {
    // STEP_OVER: 1
    //Breakpoint!
    val pos = 1

    // SMART_STEP_INTO_BY_INDEX: 3
    // STEP_OVER: 1
    Any().acceptLambda { println() }
}

// IGNORE_K2