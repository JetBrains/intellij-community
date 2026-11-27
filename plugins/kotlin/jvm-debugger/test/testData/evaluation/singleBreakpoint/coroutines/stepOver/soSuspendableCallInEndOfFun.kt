// ATTACH_JAVA_AGENT_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.10.2.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.10.2.jar)

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
