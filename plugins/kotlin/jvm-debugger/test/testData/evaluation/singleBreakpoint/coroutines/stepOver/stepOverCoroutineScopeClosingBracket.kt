// ATTACH_JAVA_AGENT_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.7.3.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.7.3.jar)

import kotlinx.coroutines.*

suspend fun foo(i: Int) {
    //Breakpoint!
    println("Start foo")
    coroutineScope {
        delay(1)
        println("coroutineScope completed")
    }
    println("coroutineScope completed $i")
    delay(1)
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