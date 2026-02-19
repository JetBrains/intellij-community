// ATTACH_JAVA_AGENT_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.7.3.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.7.3.jar)

package runToCursorWithinUndispatchedCoroutine

import kotlinx.coroutines.*

fun main() = runBlocking(Dispatchers.Default) {
    launch(start = CoroutineStart.UNDISPATCHED) {
        //Breakpoint!
        val a = 5
        delay(1)
        // RUN_TO_CURSOR: 1
        val b = 5
    }
    println("End")
}
