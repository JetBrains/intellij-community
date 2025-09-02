// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3)-javaagent

import kotlinx.coroutines.*

// This test is mainly about suspend/resume balance check. See the correspoinding changes in the code.

suspend fun main() {
    withContext(Dispatchers.Default) {
        for (i in 0 ..100) {
            launch {
                delay(10)
                foo(i)
                delay(10)
            }
        }
    }
}

private suspend fun foo(i: Int) {
    if (i == 42) {
        //Breakpoint!
        i.toString()
    }
    runBlocking {
        delay(10)
        "".toString()
        delay(10)
    }
    i.toString()
    delay(10)
}

// STEP_OVER: 2

// EXPRESSION: i
// RESULT: 42: I
