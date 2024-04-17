// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3)-javaagent

import kotlinx.coroutines.*

suspend fun foo(i: Int) {
    println("Start foo")
    coroutineScope {
        if (i == 25) {
            //Breakpoint!
            startMethod(i)
        }
        delay(10)
        // EXPRESSION: i
        // RESULT: 25: I
        println("After delay $i")
    }
    println("coroutineScope completed $i")
}

suspend fun startMethod(i: Int) {
    if (i == 25) {
        delay(10)
        "".toString()
    }
}

suspend fun endMethod(i: Int) {
    delay(10)
}

fun main() {
    runBlocking {
        repeat(100) { i ->
            launch(Dispatchers.Default) {
                foo(i)
            }
        }
    }
}

// STEP_OVER: 3
// REGISTRY: debugger.filter.breakpoints.by.coroutine.id=true