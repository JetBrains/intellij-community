package coroutinesView

// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2)-javaagent

import kotlinx.coroutines.*

fun main() {
    runBlocking(CoroutineName("root")) {
        val a = async(CoroutineName("childAsync1")) {
            delay(10)
            val b = async(CoroutineName("childAsync2")) {
                val coroutineVar = 2
                delay(20)
                "b"
            }
            delay(5)
            //Breakpoint!
            "a" + b.await()
        }
        delay(100)
        println()
    }
}