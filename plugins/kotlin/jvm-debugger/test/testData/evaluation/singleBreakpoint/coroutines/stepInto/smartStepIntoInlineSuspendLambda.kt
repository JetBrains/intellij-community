// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3)-javaagent
// REGISTRY: debugger.filter.breakpoints.by.coroutine.id=true

import kotlinx.coroutines.*

fun main() = runBlocking {
    for (i in 0 .. 100) {
        launch(Dispatchers.Default) {
            if (i == 25) {
                //Breakpoint!
                "".toString()
            }
            bar(i) {
                delay(1)
                i.toString()
            }
            bar(i) {
                delay(1)
                i.toString()
            }
        }
    }
}

private inline suspend fun bar(i: Int, lambda: (Int) -> Unit) {
    lambda(i)
    "".toString()
}

// STEP_OVER: 1
// SMART_STEP_INTO_BY_INDEX: 2
// STEP_OVER: 3
// SMART_STEP_INTO_BY_INDEX: 2
// STEP_OVER: 1

// EXPRESSION: i
// RESULT: 25: I