// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3)-javaagent


import kotlinx.coroutines.*

fun main() = runBlocking {
    //Breakpoint!
    "".toString()
    foo {
        delay(1)
        delay(1)
    }
    //Breakpoint!
    "".toString()
    foo {
        "".toString()
    }
    //Breakpoint!
    "".toString()
    bar {
        "".toString()
    }
}

private suspend fun foo(lambda: suspend () -> Unit) {
    lambda()
    "".toString()
}

private inline suspend fun bar(lambda: () -> Unit) {
    lambda()
    "".toString()
}

// STEP_OVER: 1
// SMART_STEP_INTO_BY_INDEX: 1
// SMART_STEP_INTO_BY_INDEX: 1
// RESUME: 1
// STEP_OVER: 1
// SMART_STEP_INTO_BY_INDEX: 1
// SMART_STEP_INTO_BY_INDEX: 1
// RESUME: 1
// STEP_OVER: 1
// SMART_STEP_INTO_BY_INDEX: 1
// STEP_INTO: 1
