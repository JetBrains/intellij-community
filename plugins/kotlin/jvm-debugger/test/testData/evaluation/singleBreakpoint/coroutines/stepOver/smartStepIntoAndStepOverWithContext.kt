// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3)-javaagent

import kotlinx.coroutines.*

fun main() = runBlocking {
    for (i in 0 .. 100) {
        launch(Dispatchers.Default) {
            //Breakpoint!
            i.toString()
            withContext(Dispatchers.Default) {
                delay(10)
                bar(i, 5)
                i.toString()
            }
            i.toString()
            withContext(Dispatchers.Default) {
                delay(10)
                bar(i, 10)
                i.toString()
            }
            i.toString()
        }
    }
}

suspend fun bar(x: Int, y: Int) {
    delay(10)
    x.toString()
    y.toString()
    delay(10)
}

// STEP_OVER: 1
// SMART_STEP_INTO_BY_INDEX: 1
// STEP_OVER: 6

// REGISTRY: debugger.async.stacks.coroutines=false

// Cannot get file name for `withContext` resolved call
// REGISTRY: debugger.kotlin.report.smart.step.into.targets.detection.failure=false