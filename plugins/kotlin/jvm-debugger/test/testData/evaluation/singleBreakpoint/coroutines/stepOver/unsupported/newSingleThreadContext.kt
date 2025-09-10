// ATTACH_JAVA_AGENT_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.7.3.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.7.3.jar)

import kotlinx.coroutines.*

fun main() {
    newSingleThreadContext("Ctx1").use { ctx1 ->
        newSingleThreadContext("Ctx2").use { ctx2 ->
            runBlocking(ctx1) {
                // TODO: fails to create a breakpoint on this line
                println("Started in ctx1")
                withContext(ctx2) {
                    println("Working in ctx2")
                }
                println("Back to ctx1")
            }
        }
    }
}

// STEP_OVER: 3
// REGISTRY: debugger.filter.breakpoints.by.coroutine.id=true