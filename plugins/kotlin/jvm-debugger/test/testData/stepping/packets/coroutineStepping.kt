package coroutineStepping

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

// ATTACH_JAVA_AGENT_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.10.2.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.10.2.jar)
// JVM_SYSTEM_PROPERTY: kotlinx.coroutines.debug.enable.creation.stack.trace=true


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
