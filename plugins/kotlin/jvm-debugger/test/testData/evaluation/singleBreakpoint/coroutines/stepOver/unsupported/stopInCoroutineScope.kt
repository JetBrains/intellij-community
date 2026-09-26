// ATTACH_JAVA_AGENT_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.7.3.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.7.3.jar)

import kotlinx.coroutines.*

suspend fun foo(i: Int) {
    // STEP_OVER: 2
    //Breakpoint!
    println("Start foo")
    coroutineScope { // TODO: hangs if we try to step over from this breakpoint (only in tests)
        //Breakpoint!
        delay(1)
        println("After delay")
    }
    println("coroutineScope completed")
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

// REGISTRY: debugger.filter.breakpoints.by.coroutine.id=true