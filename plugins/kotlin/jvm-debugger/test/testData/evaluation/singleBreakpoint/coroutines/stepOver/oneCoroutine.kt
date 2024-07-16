// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3)-javaagent

import kotlinx.coroutines.*

private suspend fun foo() {
    //Breakpoint!
    println("Before bar")
    delay(1)
    println("After bar")
}

fun main() {
    runBlocking {
        launch {
            foo()
        }
    }
}

// STEP_OVER: 2
// REGISTRY: debugger.filter.breakpoints.by.coroutine.id=true