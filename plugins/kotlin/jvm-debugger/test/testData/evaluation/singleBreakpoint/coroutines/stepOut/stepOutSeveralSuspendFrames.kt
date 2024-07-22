// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3)-javaagent
// REGISTRY: debugger.async.stacks.coroutines=false



package souSuspendFun

import kotlinx.coroutines.*

private fun foo(i: Int): Int {
    if (i == 25) {
        //Breakpoint!
        return 10
    }
    return 42
}

suspend fun fourth(i: Int): Int {
    delay(1)
    foo(i)
    return 5
}

suspend fun third(i: Int) : Int? {
    delay(1)
    val res = fourth(i)
    println(res)
    return res
}

suspend fun second(i: Int): Int {
    delay(1)
    return third(i)?.let { return it } ?: 0
}

suspend fun first(i: Int): Int {
    delay(1)
    second(i)
    // EXPRESSION: i
    // RESULT: 25: I
    return 12
}

fun main() = runBlocking {
    for (i in 0 .. 100) {
        launch(Dispatchers.Default) {
            first(i)
        }
    }
}

// STEP_OUT: 5