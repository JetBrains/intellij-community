// ATTACH_JAVA_AGENT_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.7.3.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.7.3.jar)

package runToCursorSuspendSameLambda

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun main() {
    val job = GlobalScope.launch {
        for (i in 1..20) {
            launch {
                if (i == 1) {
                    // EXPRESSION: i
                    // RESULT: 1: I
                    //Breakpoint!
                    startMethod(i)
                }
                delay(1000 - i*10L)
                val k = i + 1
                // EXPRESSION: k
                // RESULT: 2: I
                // RUN_TO_CURSOR: 1
                endMethod(k)
            }
        }
    }
    runBlocking {
        job.join()
    }
}

fun startMethod(i: Int) { }

fun endMethod(k: Int) { }
