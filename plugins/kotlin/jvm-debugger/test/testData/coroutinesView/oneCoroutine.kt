package coroutinesView

// ATTACH_JAVA_AGENT_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.10.2.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.10.2.jar)

import kotlinx.coroutines.*

fun main() {
    runBlocking(CoroutineName("root")) {
        a()
    }
}

suspend fun a() {
    val a = "a"
    b(a)
    val aLate = a
}

suspend fun b(paramA: String) {
    yield()
    val b = "b"
    c(b)
    val dead = paramA
}

suspend fun c(paramB: String) {
    val c = "c"
    //Breakpoint!
    val dead = paramB
}