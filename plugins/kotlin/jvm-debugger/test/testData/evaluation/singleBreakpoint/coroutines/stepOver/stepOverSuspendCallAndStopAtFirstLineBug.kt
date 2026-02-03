// ATTACH_JAVA_AGENT_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.7.3.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.7.3.jar)

// See IDEA-360063
package stepOverSuspendCallAndStopAtFirstLineBug

import kotlinx.coroutines.*
import kotlin.coroutines.*

fun main() = runBlocking {
    launch {
        foo()
    }
    println("Main end")
}

suspend fun foo() {
    //Breakpoint!
    delay(10)
    val res = readAction { "aaaa" }
    println()
}

suspend fun <T> readAction(action: () -> T): T {
    delay(10)
    return action()
}

// STEP_OVER: 3

