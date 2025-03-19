package continuation

// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-debug:1.3.8)-javaagent
// REGISTRY: debugger.async.stacks.coroutines=false

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

suspend fun main() = coroutineScope {
    launch {
        delay(1000)
        println("bye")
    }
    //Breakpoint!
    println("hello")
}