// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3)-javaagent
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
