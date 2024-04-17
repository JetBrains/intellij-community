// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3)-javaagent

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.*

fun main() = runBlocking {
    //Breakpoint!
    val deferred = GlobalScope.async {
        println("Throwing exception from async")
        throw ArithmeticException()
    }
    try {
        deferred.await()
        println("Unreached")
    } catch (e: ArithmeticException) {
        println("Caught ArithmeticException")
    }
}

// STEP_OVER: 5
// REGISTRY: debugger.filter.breakpoints.by.coroutine.id=true