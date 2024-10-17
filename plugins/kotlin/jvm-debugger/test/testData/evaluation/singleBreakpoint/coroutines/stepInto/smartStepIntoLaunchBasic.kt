// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3)
// REGISTRY: debugger.filter.breakpoints.by.coroutine.id=true

import kotlinx.coroutines.*

suspend fun startMethod(i: Int) {
    delay(1)
    "".toString()
}

suspend fun endMethod(i: Int) {
    delay(1)
}

fun main() {
    runBlocking {
        for (i in 0 .. 100) {
            if (i == 25) {
                //Breakpoint!
                "".toString()
            }
            launch {
                startMethod(i)
                delay(10)
                delay(1)
                startMethod(i)
            }
        }
    }
}

// STEP_OVER: 1
// SMART_STEP_INTO_BY_INDEX: 1
// STEP_OVER: 3