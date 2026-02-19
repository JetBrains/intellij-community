package defaultLambdaParameter

// STEP_INTO: 1
// RESUME: 1
//Breakpoint!, lambdaOrdinal = 1
fun nonInlineDefault(fn: () -> Unit = { foo() }) = fn()

// STEP_INTO: 1
// RESUME: 1
//Breakpoint!, lambdaOrdinal = 1
inline fun inlineDefault(fn: () -> Unit = { foo() }) = fn()

fun main(args: Array<String>) {
    nonInlineDefault()
    inlineDefault()
}

fun foo() = Unit
