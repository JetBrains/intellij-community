// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-debug:1.6.4)-javaagent
// REGISTRY: debugger.filter.breakpoints.by.coroutine.id=true
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
