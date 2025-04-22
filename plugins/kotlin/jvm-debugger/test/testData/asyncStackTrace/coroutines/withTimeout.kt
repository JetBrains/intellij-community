package withTimeout

// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1)-javaagent

import kotlinx.coroutines.*

fun main() {
    runBlocking {
        repeat(10) {
            launch {
                withTimeout(10) { //todo withTimeout frame is skipped
                    delay(1)
                    //Breakpoint!
                    println()
                    delay(1)
                }
            }
        }
    }
    println()
}