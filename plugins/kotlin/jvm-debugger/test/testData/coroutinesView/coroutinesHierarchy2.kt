package coroutinesView

// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2)-javaagent

import kotlinx.coroutines.*

fun main() {
    runBlocking(CoroutineName("root")) {
        println()
        //Breakpoint!
        delay(11L)
        println()
    }
}