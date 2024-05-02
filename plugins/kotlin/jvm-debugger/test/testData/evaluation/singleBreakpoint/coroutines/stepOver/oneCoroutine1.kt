// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3)-javaagent

import kotlinx.coroutines.*

fun main() {
    runBlocking {
        launch {
            //Breakpoint!
            println("Before bar")
            delay(100)
            println("After bar")
        }
    }
}

// STEP_OVER: 2
// REGISTRY: debugger.filter.breakpoints.by.coroutine.id=true