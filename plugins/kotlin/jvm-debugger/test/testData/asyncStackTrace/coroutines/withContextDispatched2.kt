package withContextDispatched2

// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1)-javaagent

import kotlinx.coroutines.*

fun main() {
    runBlocking {
        foo()
    }
}

private suspend fun foo() {
    delay(1)
    boo() // todo: foo frame is skipped, as it suspended and does not exisit on the actual thread stack
    delay(1)
}

private suspend fun boo() {
    delay(1)
    withContext(Dispatchers.Default) {
        delay(1)
        bar()
    }
    delay(1)
}

private suspend fun bar() {
    delay(1)
    //Breakpoint!
    println("bar")
    delay(1)
}
