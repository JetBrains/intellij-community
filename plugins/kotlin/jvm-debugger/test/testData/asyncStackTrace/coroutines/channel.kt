package channel

// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1)-javaagent

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

suspend fun ProducerScope<Int>.foo(i: Int) {
    delay(1)
    if (i == 5) {
        //Breakpoint!
        println()
    }
    send(i)
    delay(1)
}

fun main() = runBlocking<Unit> {
    val a = produce<Int> {
        repeat(10) { foo(it) }
    }
    for (item in a) {
        println(item)
    }
}