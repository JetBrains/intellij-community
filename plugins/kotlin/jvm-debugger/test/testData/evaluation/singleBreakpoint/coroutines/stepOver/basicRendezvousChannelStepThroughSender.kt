// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3)-javaagent

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val channel = Channel<Int>()
    launch {
        for (x in 1..5) {
            //Breakpoint!
            channel.send(x * x)
        }
        println("Sender completed")
    }
    repeat(5) {
        println(channel.receive())
    }
    println("Done!")
}

// STEP_OVER: 12
