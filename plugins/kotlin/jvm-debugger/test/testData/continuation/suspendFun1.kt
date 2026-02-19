package continuation
// ATTACH_JAVA_AGENT_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-debug-1.3.8.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.3.8.jar)

import kotlinx.coroutines.*

fun main() {
    runBlocking {
        SuspendFun1.f2()
        println()
    }
}

object SuspendFun1 {
    suspend fun f2() {
        //Breakpoint!
        yield()
        println()
    }
}
