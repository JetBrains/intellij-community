// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3)-javaagent

package soSuspendableCallInEndOfFun

import kotlinx.coroutines.*
import kotlin.coroutines.*

fun main() = runBlocking {
    val job = launch {
        inFun()
    }
    println("Main end")
}

private fun foo(a: Any) {}

suspend fun inFun() {
    println("Start")
    //Breakpoint!
    delay(1)
}

// STEP_OVER: 3
