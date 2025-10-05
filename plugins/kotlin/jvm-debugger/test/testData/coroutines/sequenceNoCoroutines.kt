package threeCoroutines

// ATTACH_JAVA_AGENT_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.10.2.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.10.2.jar)

import kotlin.random.Random

fun main() {
    sequence {
        yield(239)
        sequence {
            //Breakpoint!
            yield(666)
        }.toList()
    }.toList()
}