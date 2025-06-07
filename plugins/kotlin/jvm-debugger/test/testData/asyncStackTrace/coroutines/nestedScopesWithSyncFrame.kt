package nestedScopesWithSyncFrame

// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1)-javaagent

import kotlinx.coroutines.*

fun main() {
    runBlocking {
        withContext(Dispatchers.Default) {
            //Breakpoint!
            println()
            foo { // todo foo frame is missing from the stacktrace, it was suspended, removed from the actual stack trace and not captured
                 //Breakpoint!
                delay(1)
                sync {
                    //Breakpoint!
                    println()
                    launch {
                        //Breakpoint!
                        println()
                        withContext(Dispatchers.IO) {
                            //Breakpoint!
                            println()
                            delay(1)
                            joo {
                                //Breakpoint!
                                println()
                                withTimeout(100000) {
                                    //Breakpoint!
                                    println()
                                    delay(1)
                                }
                            }
                        }
                    }
                }
            }
        }
        delay(1)
    }
}

private suspend fun foo(f: suspend () -> Unit) {
    delay(1)
    f()
    delay(1)
}

private suspend fun joo(f: suspend () -> Unit) {
    delay(1)
    f()
    delay(1)
}

private fun sync(f: suspend () -> Unit) {
    runBlocking {
        delay(1)
        f()
        delay(1)
    }
}