// ATTACH_JAVA_AGENT_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.7.3.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.7.3.jar)

package runToCursorIntoWithContext

import kotlinx.coroutines.*

fun main() = runBlocking {
    //Breakpoint!
    println()
    val name = withContext(Dispatchers.Default) {
        // RUN_TO_CURSOR: 1
        val threadName = Thread.currentThread().name
        delay(10)
        threadName
    }
    println(name)
}
