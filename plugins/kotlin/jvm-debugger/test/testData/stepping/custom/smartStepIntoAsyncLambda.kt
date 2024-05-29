package smartStepIntoAsyncLambda

fun lambdaInSequence() {
    val ranges = listOf(1, 2, 3)
    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_OUT: 1
    // RESUME: 1
    //Breakpoint!
    ranges.asSequence()
        .map {
            it
        }
        .forEach(::println)
}

fun lambdaInStream() {
    val ranges = listOf(1, 2, 3)
    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_OUT: 1
    //Breakpoint!
    ranges.stream()
        .map {
            it
        }
        .forEach(::println)
}

fun main() {
    lambdaInSequence()
    lambdaInStream()
}
