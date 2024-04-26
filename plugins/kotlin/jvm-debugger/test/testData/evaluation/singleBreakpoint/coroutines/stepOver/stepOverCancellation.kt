// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3)-javaagent

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.*

fun main() = runBlocking {
    val deferred = GlobalScope.async {
        //Breakpoint!
        println("Throwing exception from async")
        delay(10)
        throw ArithmeticException()
    }
    try {
        deferred.await()
        println("Unreached")
    } catch (e: ArithmeticException) {
        println("Caught ArithmeticException")
    }
}

// STEP_OVER: 4
// REGISTRY: debugger.filter.breakpoints.by.coroutine.id=true