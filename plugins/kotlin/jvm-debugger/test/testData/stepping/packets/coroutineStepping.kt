package coroutineStepping

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-debug:1.3.8)-javaagent
// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4)


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
