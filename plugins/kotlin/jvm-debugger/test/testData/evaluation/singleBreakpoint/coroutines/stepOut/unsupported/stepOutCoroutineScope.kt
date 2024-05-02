// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3)-javaagent

package stepOutCoroutineScope

import kotlinx.coroutines.*
// TODO: what behaviour is expected? Should we get to line 14?
suspend fun foo(i: Int) {
    println("Start foo")
    coroutineScope { // TODO: hangs if we try to step over from this breakpoint (only in tests)
        //Breakpoint!
        delay(100)
        println("After delay")
    }
    println("coroutineScope completed")
}

fun main() {
    runBlocking {
        for (i in 1 .. 3) {
            launch {
                foo(i)
            }
        }
    }
}

// STEP_OUT: 1