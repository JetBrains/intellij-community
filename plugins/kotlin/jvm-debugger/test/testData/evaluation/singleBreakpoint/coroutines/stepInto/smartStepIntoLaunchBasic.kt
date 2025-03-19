// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3)
// REGISTRY: debugger.filter.breakpoints.by.coroutine.id=true

import kotlinx.coroutines.*

suspend fun foo(i: Int) {
    coroutineScope {
        if (i == 1) {
            //Breakpoint!
            i.toString()
        }
        launch(Dispatchers.Default) {
            delay(1)
            launch(Dispatchers.Default) {
                delay(1)
                launch(Dispatchers.Default) {
                    delay(1)
                    launch(Dispatchers.Default) {
                        delay(1)
                        launch(Dispatchers.Default) {
                            delay(1)
                            launch(Dispatchers.Default) {
                                startMethod(i)
                                delay(1)
                                println("After delay $i")
                            }
                            delay(1)
                        }
                        delay(1)
                    }
                }
            }
        }
    }
    println("coroutineScope completed $i")
}

suspend fun startMethod(i: Int) {
    if (i == 5) {
        delay(1)
        "".toString()
    }
}

suspend fun endMethod(i: Int) {
    delay(1)
}

fun main() {
    runBlocking {
        for (i in 0 .. 1000) {
            launch {
                foo(i)
            }
        }
    }
}

// STEP_OVER: 1
// SMART_STEP_INTO_BY_INDEX: 1
// STEP_OVER: 2
// SMART_STEP_INTO_BY_INDEX: 1
// STEP_OVER: 2
// SMART_STEP_INTO_BY_INDEX: 1
// STEP_OVER: 2
// SMART_STEP_INTO_BY_INDEX: 1
// STEP_OVER: 2
// SMART_STEP_INTO_BY_INDEX: 1
// STEP_OVER: 2
// SMART_STEP_INTO_BY_INDEX: 1
// STEP_OVER: 3