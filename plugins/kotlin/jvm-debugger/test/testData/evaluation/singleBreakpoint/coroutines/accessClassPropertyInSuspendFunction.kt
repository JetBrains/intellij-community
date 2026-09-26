package accessClassPropertyInSuspendFunction

// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.4.2.jar)

import kotlinx.coroutines.runBlocking

class A {
    val i = 1

    suspend fun foo() {
        // EXPRESSION: i
        // RESULT: 1: I
        //Breakpoint!
        println("")
    }
}

fun main() = runBlocking {
    A().foo()
}