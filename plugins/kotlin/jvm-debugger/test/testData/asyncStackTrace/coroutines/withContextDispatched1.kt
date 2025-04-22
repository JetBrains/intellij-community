package withContextDispatched1

// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1)-javaagent

import kotlinx.coroutines.*

fun main() = runBlocking {
    println("0: ${Thread.currentThread()}")
    withContext(Dispatchers.Default) {
        //Breakpoint!
        println("1: ${Thread.currentThread()}")
        delay(1)
    }
}
