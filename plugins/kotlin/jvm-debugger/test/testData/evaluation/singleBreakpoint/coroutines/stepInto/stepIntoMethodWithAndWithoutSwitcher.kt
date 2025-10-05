// ATTACH_JAVA_AGENT_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.7.3.jar)
// ATTACH_LIBRARY_BY_LABEL: sources(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.7.3-sources.jar)


import kotlinx.coroutines.*

// No suspend calls, so no switcher
suspend fun foo() {
    "".toString()
    "".toString()
}

// Tail call optimization: no switcher here
suspend fun boo() {
    foo()
}

// 2 suspend calls, so there is switcher here
suspend fun bar() {
    boo()
    delay(1)
}

fun main() = runBlocking {
    //Breakpoint!
    bar()
}

// STEP_INTO: 3
