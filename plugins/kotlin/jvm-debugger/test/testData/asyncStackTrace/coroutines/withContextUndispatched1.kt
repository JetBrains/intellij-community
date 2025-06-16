package withContextUndispatched1

// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1)-javaagent

import kotlinx.coroutines.*

fun main() = runBlocking {
    println("0: ${Thread.currentThread()}")
    launch(Dispatchers.Default) {
        withContext(Dispatchers.Default) {
            //Breakpoint!
            println("1: ${Thread.currentThread()}")
            delay(1)
        }
    }
    println("2: ${Thread.currentThread()}")
}
