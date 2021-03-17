package twoDumps

// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-debug:1.3.8)-javaagent

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

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
