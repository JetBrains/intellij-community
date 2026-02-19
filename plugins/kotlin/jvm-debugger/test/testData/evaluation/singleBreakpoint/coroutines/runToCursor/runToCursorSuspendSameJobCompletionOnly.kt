// ATTACH_JAVA_AGENT_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.7.3.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.7.3.jar)

package runToCursorSuspendSameJobCompletionOnly

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun main() {
    val job = GlobalScope.launch {
        for (i in 1..20) {
            launch {
                startMethod(i)
                delay(1000 - i*10L)
                endMethod(i)
            }
        }
    }
    runBlocking {
        job.join()
    }
}

suspend fun startMethod(i: Int) {
    if (i == 1) {
        //Breakpoint!
        "".toString()
    }
}

suspend fun endMethod(k: Int) {
    // EXPRESSION: k
    // RESULT: 1: I
    // RUN_TO_CURSOR: 1
    delay(1)
}
