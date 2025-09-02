package framesAboveCoroutineOwner

// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1)-javaagent

import kotlinx.coroutines.*

// Note: In this example it would be not enough to just collect the whole continuation stack
// for the top stack frame. This continuiation stack would end on the CoroutineOwner corresponding to the inner launched coroutine,
// and the frames above (withTimeout and withContext) will be skipped.
fun main() = runBlocking {
    println("0: ${Thread.currentThread()}")
    withTimeout(100000) {
        withContext(Dispatchers.Default) {
            launch(Dispatchers.Default) { // new coroutine
                withContext(Dispatchers.Default) {
                    println("1: ${Thread.currentThread()}")
                    //Breakpoint!
                    delay(1)
                }
            }
        }
    }
    println()
}

