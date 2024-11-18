package veryLongCoroutineStack

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay

// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-debug:1.3.8)-javaagent
// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4)

suspend fun foo(n: Int) {
    if (n == 0) {
        delay(1)
    } else {
        foo(n - 1)
    }
    val n1 = 42 // to prevent stackFrame to collapse
}

fun main(): Unit = runBlocking {
    //Breakpoint!
    foo(20)
}

// STEP_INTO: 100
