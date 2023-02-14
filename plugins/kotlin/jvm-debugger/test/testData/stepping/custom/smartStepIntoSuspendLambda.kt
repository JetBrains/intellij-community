// ATTACH_LIBRARY: coroutines

package smartStepIntoSuspendLambda

import forTests.builder

fun main() {
    // STEP_OVER: 1
    //Breakpoint!
    println()

    // SMART_STEP_INTO_BY_INDEX: 2
    builder {
        println()
    }
}

// IGNORE_K2
