// ATTACH_JAVA_AGENT_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.7.3.jar)
// ATTACH_LIBRARY_BY_LABEL: sources(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.7.3-sources.jar)
// REGISTRY: debugger.filter.breakpoints.by.coroutine.id=true

package smartStepIntoSuspendLambda

import kotlinx.coroutines.*

fun main() {
    runBlocking {
        // STEP_OVER: 1
        //Breakpoint!
        println()

        // SMART_STEP_INTO_BY_INDEX: 1
        // STEP_OVER: 1
        launch {
            println()
        }
    }
}
