// ATTACH_LIBRARY: coroutines

package coroutine

import forTests.builder

suspend fun second() {
}

suspend fun first() {
    second()
}

fun main(args: Array<String>) {
    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_OVER: 2
    //Breakpoint!
    builder {
        first()
    }
}

// IGNORE_LOG_ERRORS
