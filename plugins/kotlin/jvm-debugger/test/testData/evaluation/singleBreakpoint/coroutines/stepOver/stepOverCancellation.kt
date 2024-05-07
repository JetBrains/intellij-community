// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3)-javaagent

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val deferred = GlobalScope.async {
        //Breakpoint!
        println("Throwing exception from async")
        delay(1)
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
// REGISTRY: debugger.always.suspend.thread.before.switch=true