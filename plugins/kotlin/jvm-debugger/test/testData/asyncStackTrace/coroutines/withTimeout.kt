package withTimeout

// ATTACH_JAVA_AGENT_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.10.1.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.10.1.jar)

import kotlinx.coroutines.*

fun main() {
    runBlocking {
        launch {
            withContext(Dispatchers.Default) {
                val res = withTimeout(100) {
                    try {
                        //Breakpoint!
                        delay(1)
                        5
                    } catch (e: CancellationException) {
                        -1
                    }
                }
                assert(res == 5)
            }
        }
    }
}