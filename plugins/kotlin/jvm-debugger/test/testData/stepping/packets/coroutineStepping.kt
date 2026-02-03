package coroutineStepping

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

// ATTACH_JAVA_AGENT_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-debug-1.3.8.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.3.8.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.6.4.jar)


fun main(): Unit = runBlocking {
    launch {
        launch {
            launch {
                launch {
                    //Breakpoint!
                    println()
                    for (i in 1..5) {
                        yield()
                    }
                }
            }
        }
    }
}

// STEP_OVER: 10
