package suspendFramesBeforeLaunch

// ATTACH_JAVA_AGENT_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.10.1.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.10.1.jar)

import kotlinx.coroutines.*

fun main() = runBlocking {
    println()
    doo {
        delay(1)
        koo {
            delay(1)
            launch {
                println()
                //Breakpoint!
                delay(1)
                println()
            }
        }
    }
}

private suspend fun doo (f: suspend () -> Unit) {
    delay(1)
    f()
    delay(1)
}

private suspend fun koo (f: suspend () -> Unit) {
    delay(1)
    f()
    delay(1)
}
