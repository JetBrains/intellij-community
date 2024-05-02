// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3)-javaagent
// REGISTRY: debugger.filter.breakpoints.by.coroutine.id=true

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
    delay(100)
}

fun main() = runBlocking {
    //Breakpoint!
    bar()
}

// STEP_INTO: 3
