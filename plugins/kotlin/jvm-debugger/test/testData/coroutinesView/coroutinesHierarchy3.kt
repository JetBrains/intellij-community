package coroutinesView

// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2)-javaagent

import kotlinx.coroutines.*

fun main() {
    runBlocking(CoroutineName("root")) {
        test()
    }
}

suspend fun test() {
    coroutineScope {
        launch(CoroutineName("launch1")) {
            delay(1)
            println()
            delay(1)
        }
        launch(CoroutineName("launch2")) {
            coroutineScope {
                val a = async(CoroutineName("childAsync1")) {
                    val coroutineVar = 1
                    delay(100)
                    println("coroutineVar=$coroutineVar")
                    6
                }

                val b = async(CoroutineName("childAsync2")) {
                    val coroutineVar = 2
                    delay(100)
                    println("coroutineVar=$coroutineVar")
                    7
                }
            }
        }
        withContext(Dispatchers.Default) {
            coroutineScope {
                val a = async(CoroutineName("async1")) {
                    val coroutineVar = 1
                    delay(100)
                    println("coroutineVar=$coroutineVar") // ← breakpoint
                    6
                }

                val b = async(CoroutineName("async2")) {
                    val coroutineVar = 2
                    delay(100)
                    println("coroutineVar=$coroutineVar")
                    7
                }
                //Breakpoint!
                println("result: ${a.await() * b.await()}")
            }
        }
    }
}