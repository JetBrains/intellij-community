package channel

// ATTACH_JAVA_AGENT_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.10.1.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.10.1.jar)

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

suspend fun ProducerScope<Int>.foo(i: Int) {
    delay(1)
    if (i == 5) {
        //Breakpoint!
        println()
    }
    send(i)
    delay(1)
}

fun main() = runBlocking<Unit> {
    val a = produce<Int> {
        repeat(10) { foo(it) }
    }
    for (item in a) {
        println(item)
    }
}