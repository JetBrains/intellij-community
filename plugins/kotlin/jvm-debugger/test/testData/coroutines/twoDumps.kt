package twoDumps

// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2)-javaagent
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