package defaultLambdaParameter

fun nonInlineDefault(fn: () -> Unit = { println() }) {
    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!
    fn()
}

inline fun inlineDefault(fn: () -> Unit = { println() }) {
    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!
    fn()
}

fun main(args: Array<String>) {
    nonInlineDefault()
    inlineDefault()
}
