package withContextUndispatched1

// ATTACH_JAVA_AGENT_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.10.1.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.10.1.jar)

import kotlinx.coroutines.*

fun main() = runBlocking {
    println("0: ${Thread.currentThread()}")
    launch(Dispatchers.Default) {
        withContext(Dispatchers.Default) {
            //Breakpoint!
            println("1: ${Thread.currentThread()}")
            delay(1)
        }
    }
    println("2: ${Thread.currentThread()}")
}
