// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3)-javaagent

package runToCursorWithinUndispatchedCoroutine

import kotlinx.coroutines.*

fun main() {
    MyTest1().start();
}

class MyTest1 {
    fun start() = runBlocking {
        launch {
            for (i in 1 .. 10) {
                launch {
                    worker(i)
                }
            }
        }
    }

    suspend fun worker(i: Int) {
        coroutineScope {
            launch(start = CoroutineStart.UNDISPATCHED) {
                //Breakpoint!
                val a = 5
                withContext(Dispatchers.IO) {
                    println("x")
                }
                // RUN_TO_CURSOR: 1
                val b = 5
            }
        }
    }
}
