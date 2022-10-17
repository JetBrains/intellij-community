package smartStepIntoLambdaWithparametersDestructuring

fun main() {
    // STEP_OVER: 1
    //Breakpoint!
    val list = listOf(1, 2, 3)

    // SMART_STEP_INTO_BY_INDEX: 2
    list.withIndex().find { (i, value) ->
        i + 1 > value
    }
}

// IGNORE_K2
