package coroutinesView

// ATTACH_JAVA_AGENT_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.10.2.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.10.2.jar)

import kotlinx.coroutines.*

fun main() {
    runBlocking(CoroutineName("root")) {
        println()
        //Breakpoint!
        delay(11L)
        println()
    }
}