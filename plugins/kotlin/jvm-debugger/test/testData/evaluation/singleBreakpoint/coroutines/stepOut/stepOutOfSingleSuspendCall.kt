// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3)-javaagent
// REGISTRY: debugger.async.stacks.coroutines=false



import kotlinx.coroutines.*

private fun CoroutineScope.work(i: Int) {
    launch(Dispatchers.Default) {
        delay(10)
        val x = funWithSuspendLast(i)
        // EXPRESSION: i
        // RESULT: 25: I
        println("x = $x")
        delay(1)
        println("i = $i")
    }
}


suspend fun funWithSuspendLast(i: Int): Int {
    if (i == 25) {
        delay(10)
        //Breakpoint!
        val res = someInt()
        delay(10)
        return res
    } else {
        return 10
    }
}

suspend fun someInt(): Int {
    delay(10)
    return 42
}

fun main() = runBlocking  {
    coroutineScope {
        work(-1)
    }
    for (i in 0..100) {
        work(i)
    }
}


// STEP_OUT: 1
// STEP_OVER: 3
