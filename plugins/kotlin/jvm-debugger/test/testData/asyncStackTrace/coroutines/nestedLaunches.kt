package nestedLaunches

// ATTACH_JAVA_AGENT_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.10.1.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.10.1.jar)

import kotlinx.coroutines.*

suspend fun main() =
    withContext(Dispatchers.Default) {
        delay(1)
        launch {
            println("Launch 1")
            launch {
                delay(1)
                println("Launch 2")
                delay(1)
            }
            launch(Dispatchers.Default) {
                delay(1)
                launch {
                    delay(1)
                    bar()
                    delay(1)
                }
            }
        }
        println()
    }

private suspend fun bar() {
    delay(1)
    //Breakpoint!
    println("bar")
    delay(1)
}


