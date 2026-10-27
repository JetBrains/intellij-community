// ATTACH_JAVA_AGENT_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.7.3.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.7.3.jar)

package runToCursorIntoCoroutineScopeAndAsync

import kotlinx.coroutines.*

fun main() = runBlocking {
    //Breakpoint!
    println()
    val result = coroutineScope {
        // RUN_TO_CURSOR: 1
        val one = async<Int> {
            // RUN_TO_CURSOR: 2
            delay(10)
            3
        }
        val two = async(start = CoroutineStart.ATOMIC) {
            delay(10)
            42
        }
        one.await() + two.await()
    }
    println(result)
}
