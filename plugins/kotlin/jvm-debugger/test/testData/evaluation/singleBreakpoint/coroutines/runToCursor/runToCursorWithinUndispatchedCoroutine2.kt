// ATTACH_JAVA_AGENT_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.7.3.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.7.3.jar)

package runToCursorWithinUndispatchedCoroutine

import kotlinx.coroutines.*

fun main() {
    MyTest1().start();
}

class MyTest1 {
    fun start() = runBlocking(Dispatchers.Default) {
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
                if (i == 5) {
                    //Breakpoint!
                    val a = 5
                }
                withContext(Dispatchers.IO) {
                    println("x")
                }
                // EXPRESSION: i
                // RESULT: 5: I
                // RUN_TO_CURSOR: 1
                val b = 5 + i
            }
        }
    }
}
