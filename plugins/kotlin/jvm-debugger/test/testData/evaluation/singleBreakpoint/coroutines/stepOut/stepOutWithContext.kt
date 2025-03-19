// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3)-javaagent
// REGISTRY: debugger.async.stacks.coroutines=false



package stepOutCoroutineScope

import kotlinx.coroutines.*

suspend fun foo(i: Int) {
    println("Start foo")
    var counter = 0
    withContext(Dispatchers.Default) {
        if (i == 25) {
            //Breakpoint!
            delay(1)
            counter++
            println("After delay")
        }
        counter++
        delay(10)
        counter++
    }
    delay(1)
    i.toString()
    counter.toString()
}

fun main() {
    runBlocking {
        for (i in 1 .. 100) {
            launch(Dispatchers.Default) {
                foo(i)
            }
        }
    }
}

// STEP_OUT: 1
// STEP_OVER: 2

// EXPRESSION: i
// RESULT: 25: I

// EXPRESSION: counter
// RESULT: 3: I
