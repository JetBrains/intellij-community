package breakpointInLambdaWithNestedLambda

fun <T> lambda(obj: T, f: ((T) -> Unit)?) = if (f == null) Unit else f(obj)
inline fun <T> inlineLambda(obj: T, f: ((T) -> Unit)) = f(obj)
fun <T> nestedLambda(obj: T, f: ((T) -> Unit)?) = if (f == null) Unit else f(obj)
inline fun <T> inlineNestedLambda(obj: T, f: ((T) -> Unit)) = f(obj)
fun <T> consume(obj: T) = Unit

fun main() {
    testLambdaWithNestedLambda()
    testInlineLambdaWithNestedLambda()
    testLambdaWithNestedInlineLambda()
    testInlineLambdaWithNestedInlineLambda()
}

fun testLambdaWithNestedLambda() {
    // STEP_INTO: 1
    //Breakpoint!, lambdaOrdinal = 1
    lambda("first") { nestedLambda("second") { consume("third $it ") } }

    lambda("first") {
        // RESUME: 1
        // STEP_INTO: 1
        //Breakpoint!
        nestedLambda("second", null)
    }

    lambda("first") {
        // RESUME: 1
        // STEP_INTO: 1
        //Breakpoint!, lambdaOrdinal = -1
        nestedLambda("second") { consume("third $it") }
    }
    // RESUME: 1
}

fun testInlineLambdaWithNestedLambda() {
    // STEP_INTO: 1
    //Breakpoint!, lambdaOrdinal = 1
    inlineLambda("first") { nestedLambda("second") { consume("third $it ") } }

    inlineLambda("first") {
        // RESUME: 1
        // STEP_INTO: 1
        //Breakpoint!
        nestedLambda("second", null)
    }

    inlineLambda("first") {
        // RESUME: 1
        // STEP_INTO: 1
        //Breakpoint!, lambdaOrdinal = -1
        nestedLambda("second") { consume("third $it") }
    }
    // RESUME: 1
}



fun testLambdaWithNestedInlineLambda() {
    // STEP_INTO: 1
    //Breakpoint!, lambdaOrdinal = 1
    lambda("first") { inlineNestedLambda("second") { consume("third $it ") } }

    lambda("first") {
        // RESUME: 1
        // STEP_INTO: 1
        //Breakpoint!, lambdaOrdinal = -1
        inlineNestedLambda("second") { consume("third $it") }
    }
    // RESUME: 1
}

fun testInlineLambdaWithNestedInlineLambda() {
    // STEP_INTO: 1
    //Breakpoint!, lambdaOrdinal = 1
    inlineLambda("first") { inlineNestedLambda("second") { consume("third $it ") } }

    inlineLambda("first") {
        // RESUME: 1
        // STEP_INTO: 1
        //Breakpoint!, lambdaOrdinal = -1
        inlineNestedLambda("second") { consume("third $it") }
    }
    // RESUME: 1
}

