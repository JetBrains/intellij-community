package withContextFrameIsNotRemovedAfterExit

// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1)-javaagent

import kotlinx.coroutines.*

fun main() = runBlocking {
    println()
    doo {
        println()
        koo {
            println()
            withContext(Dispatchers.Default) { // todo this frame should not be present in the stack trace
                println()
                delay(1)
                println()
            }
            withContext(Dispatchers.Default) {
                println()
                //Breakpoint!
                delay(1)
                println()
            }
            println()
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
