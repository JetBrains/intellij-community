// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3)-javaagent

import kotlinx.coroutines.*
import kotlin.system.measureTimeMillis

suspend fun compute(action: suspend () -> Unit) {
    val n = 2 
    val k = 10
    val time = measureTimeMillis {
        coroutineScope {
            repeat(n) {
                launch {
                    repeat(k) { action() }
                }
            }
        }
    }
    println("Completed")
}

var counter = 0

fun main() = runBlocking {
    for (i in 0 ..100) {
        launch(Dispatchers.Default) {
            if (i == 25) {
                //Breakpoint!
                i.toString()
                delay(10)
            }
            withContext(Dispatchers.Default) {
                compute {
                    counter++
                }
            }
            i.toString()
            withContext(Dispatchers.Unconfined) {
                compute {
                    counter++
                }
            }
        }
    }
    println("Counter = $counter")
}

// STEP_OVER: 6

// EXPRESSION: i
// RESULT: 25: I

// REGISTRY: debugger.async.stacks.coroutines=false