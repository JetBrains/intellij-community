// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3)-javaagent

import kotlinx.coroutines.*

suspend fun foo(i: Int) {
    println("Start foo")
    //Breakpoint!
    coroutineScope {
        startMethod(i)
        delay(100)
        println("After delay $i")
    }
    println("coroutineScope completed $i")
}

suspend fun startMethod(i: Int) {
    if (i == 5) {
        delay(100)
        "".toString()
    }
}

suspend fun endMethod(i: Int) {
    delay(100)
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

// STEP_INTO: 1
// STEP_OVER: 1
// REGISTRY: debugger.filter.breakpoints.by.coroutine.id=true