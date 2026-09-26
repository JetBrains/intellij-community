// ATTACH_JAVA_AGENT_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.7.3.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.7.3.jar)

import kotlinx.coroutines.*

fun main() {
    runBlocking {
        launch {
            //Breakpoint!
            println("Before bar")
            delay(1)
            println("After bar")
        }
    }
}

// STEP_OVER: 2
// REGISTRY: debugger.filter.breakpoints.by.coroutine.id=true