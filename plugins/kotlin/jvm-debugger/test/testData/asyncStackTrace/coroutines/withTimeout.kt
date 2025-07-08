package withTimeout

// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1)-javaagent

import kotlinx.coroutines.*

fun main() {
    runBlocking {
        launch {
            withContext(Dispatchers.Default) {
                val res = withTimeout(100) {
                    try {
                        //Breakpoint!
                        delay(1)
                        5
                    } catch (e: CancellationException) {
                        -1
                    }
                }
                assert(res == 5)
            }
        }
    }
}