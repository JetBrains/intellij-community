package continuation
// ATTACH_JAVA_AGENT_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-debug-1.3.8.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.3.8.jar)
// REGISTRY: debugger.async.stacks.coroutines=false

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

fun main() {
    val main = "main"
    runBlocking {
        a()
    }
}

suspend fun a() {
    val a = "a"
    b(a)
    val aLate = a // to prevent stackFrame to collapse
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