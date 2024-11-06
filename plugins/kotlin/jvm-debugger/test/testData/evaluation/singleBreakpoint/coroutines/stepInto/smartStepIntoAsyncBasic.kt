// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3)-javaagent
// REGISTRY: debugger.filter.breakpoints.by.coroutine.id=true

import kotlinx.coroutines.*

suspend fun foo(i: Int) {
    coroutineScope {
        if (i == 34) {
            //Breakpoint!
            "".toString()
        }
        val res = async(Dispatchers.Default) {
            delay(10)
            delay(10)
            "After delay $i"
        }
        res.join()
        println("Obtained result $res")
    }
    println("coroutineScope completed $i")
}

private suspend fun foo(lambda: suspend () -> Unit) {
    lambda()
    "".toString()
}

fun main() {
    runBlocking {
        for (i in 0 .. 1000) {
            launch {
                foo(i)
            }
        }
    }
}

// STEP_OVER: 1
// SMART_STEP_INTO_BY_INDEX: 1
// STEP_OVER: 3