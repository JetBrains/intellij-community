package accessClassPropertyInSuspendFunction

// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2)

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