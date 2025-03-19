// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3)-javaagent
// REGISTRY: debugger.filter.breakpoints.by.coroutine.id=true
// REGISTRY: debugger.async.stacks.coroutines=false

import kotlinx.coroutines.*

suspend fun foo(i: Int) {
    if (i == 25) {
        //Breakpoint!
        i.toString()
    }
    withContext(Dispatchers.IO) {
        startMethod(i)
        delay(1)
        delay(1)
        endMethod(i)
    }
    delay(10)
    println("aaa")
    delay(1)
}

private suspend fun foo(lambda: suspend () -> Unit) {
    lambda()
    "".toString()
}

suspend fun startMethod(i: Int) {
    if (i == 5) {
        delay(1)
        "".toString()
    }
}

suspend fun endMethod(i: Int) {
    delay(1)
}

fun main() {
    runBlocking {
        for (i in 0 .. 1000) {
            launch(Dispatchers.Default) {
                foo(i)
            }
        }
    }
}

// STEP_OVER: 1
// SMART_STEP_INTO_BY_INDEX: 1
// STEP_OVER: 2

// EXPRESSION: i
// RESULT: 25: I