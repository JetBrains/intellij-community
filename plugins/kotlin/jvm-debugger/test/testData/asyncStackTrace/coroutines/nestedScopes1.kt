package nestedScopes1

// ATTACH_JAVA_AGENT_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.10.1.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.10.1.jar)

import kotlinx.coroutines.*

fun main() = runBlocking {
    val future = CompletableDeferred<Int>()
    println("0: ${Thread.currentThread()}")
    coroutineScope {
        //Breakpoint!
        println("1: ${Thread.currentThread()}")
        withContext(Dispatchers.IO) {
            //Breakpoint!
            println("2: ${Thread.currentThread()}")
            doo {
                delay(1)
                //Breakpoint!
                println("3: ${Thread.currentThread()}")
                withTimeout(10000000) {
                    //Breakpoint!
                    println("4: ${Thread.currentThread()}")
                    withContext(Dispatchers.Default) {
                        println("5: ${Thread.currentThread()}")
                        //Breakpoint!
                        future.complete(42)
                    }
                }
            }
            delay(1)
        }
    }
    println(future)
}

private suspend fun doo (f: suspend () -> Unit) {
    delay(1)
    f()
    delay(1)
}