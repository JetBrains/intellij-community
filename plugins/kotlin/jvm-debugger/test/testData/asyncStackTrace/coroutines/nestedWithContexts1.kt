package nestedWithContexts1

// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1)-javaagent

import kotlinx.coroutines.*

fun main() = runBlocking {
    val future = CompletableDeferred<Int>()
    //Breakpoint!
    println("0: ${Thread.currentThread()}")
    withContext(Dispatchers.Default) {
        //Breakpoint!
        println("1: ${Thread.currentThread()}")
        withContext(Dispatchers.Default) {
            println("2: ${Thread.currentThread()}")
            //Breakpoint!
            delay(1)
        }
    }
    println(future)
}