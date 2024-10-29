// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3)-javaagent
// REGISTRY: debugger.async.stacks.coroutines=false



import kotlinx.coroutines.*

fun main() {
    runBlocking  {
        for (i in 0..100) {
            launch(Dispatchers.Default) {
                val x = funWithSuspendLast(i)
                println("x = $x")
                delay(1)
                println("i = $i")
            }
        }
    }
}

suspend fun funWithSuspendLast(i: Int): Int {
    if (i == 25) {
        //Breakpoint!
        someInt()
        error("This exception should be thrown")
    } else {
        return 10
    }
}

suspend fun someInt(): Int {
    delay(1)
    return 42
}

// STEP_OUT: 1
// STEP_OVER: 3
