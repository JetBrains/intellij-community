package veryLongCoroutineStack

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay

// ATTACH_JAVA_AGENT_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.10.2.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.10.2.jar)
// JVM_SYSTEM_PROPERTY: kotlinx.coroutines.debug.enable.creation.stack.trace=true

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
