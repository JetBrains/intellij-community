// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4)
// KTIJ-23430
package smartStepIntoSuspendFunInterface

import kotlinx.coroutines.flow.asFlow

suspend fun main() {
    // Flow.collect() uses a FlowCollector which is a functional interface witha  suspend method.

    // SMART_STEP_INTO_BY_INDEX: 4
    // RESUME: 1
    //Breakpoint!
    listOf(1).asFlow().collect {
        println(it)
    }

    // The following behavior seems to be identical to the Flow.collect() example but prior to fixing KTIJ-23430, it behaves differently.
    // With Flow.collect(), stepping into the lambda steps over it, but with this example, the debugger hangs.

    // SMART_STEP_INTO_BY_INDEX: 3
    //Breakpoint!
    listOf(1).collect {
        println(it)
    }
}

private suspend fun <T> Iterable<T>.collect(visitor: FlowCollector<T>) = forEach { visitor.emit(it) }

private fun interface FlowCollector<in T> {
    suspend fun emit(value: T)
}

// IGNORE_K2
