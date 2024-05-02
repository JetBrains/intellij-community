// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3)-javaagent

import kotlinx.coroutines.*

suspend fun foo(i: Int) {
    //Breakpoint!
    println("Start foo")
    coroutineScope {
        delay(100)
        println("coroutineScope completed")
    }
    println("coroutineScope completed $i")
    delay(100)
    println("End foo")
}

fun main() {
    runBlocking {
        for (i in 1 .. 3) {
            launch {
                foo(i)
            }
        }
    }
}

// STEP_OVER: 5
// REGISTRY: debugger.filter.breakpoints.by.coroutine.id=true