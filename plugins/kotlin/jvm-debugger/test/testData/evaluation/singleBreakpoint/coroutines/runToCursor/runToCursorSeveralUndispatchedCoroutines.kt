// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3)-javaagent

package runToCursorWithinUndispatchedCoroutine

import kotlinx.coroutines.*

fun main() = runBlocking {
    for (i in 0 .. 10) {
        launch(start = CoroutineStart.UNDISPATCHED) {
            if (i == 5) {
                //Breakpoint!
                foo(i)
            }
            delay(1)
            // EXPRESSION: i
            // RESULT: 5: I
            // RUN_TO_CURSOR: 1
            val b = 5
        }
    }
    println("End")
}

suspend fun foo(i: Int) {
    delay(1)
    println("End foo $i")
}
