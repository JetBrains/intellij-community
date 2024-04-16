// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3)-javaagent

import kotlinx.coroutines.*

fun main() {
    runBlocking {
        for (i in 1 .. 100) {
            launch(Dispatchers.Default) {
                if (i == 5) {
                    //Breakpoint!
                    startMethod(i)
                }
                delay(10)
                endMethod(i)
                // EXPRESSION: i
                // RESULT: 5: I
                println(i)
            }
        }
    }
}

suspend fun startMethod(i: Int) {
    if (i == 5) {
        delay(10)
        "".toString()
    }
}

suspend fun endMethod(i: Int) {
    delay(10)
    println("End")
}

// STEP_OVER: 4
// REGISTRY: debugger.filter.breakpoints.by.coroutine.id=true