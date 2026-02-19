package twoDumps

// ATTACH_JAVA_AGENT_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.10.2.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.10.2.jar)
// REGISTRY: debugger.async.stacks.coroutines=false

import kotlinx.coroutines.*

suspend fun foo() {
    //Breakpoint!
    println("")
}

suspend fun bar() {
    //Breakpoint!
    println("")
}

fun main() = runBlocking<Unit> {
    foo()
    runBlocking {
        bar()
    }
}