package veryLongCoroutineStack

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay

// ATTACH_JAVA_AGENT_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-debug-1.3.8.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.3.8.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.6.4.jar)

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
