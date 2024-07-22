// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3)-javaagent

package runToCursorWithinUndispatchedCoroutine

import kotlinx.coroutines.*

fun main() = runBlocking {
    launch(start = CoroutineStart.UNDISPATCHED) {
        //Breakpoint!
        val a = 5
        delay(1)
        // RUN_TO_CURSOR: 1
        val b = 5
    }
    println("End")
}
