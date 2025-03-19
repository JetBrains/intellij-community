package continuation
// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-debug:1.3.8)-javaagent

import kotlinx.coroutines.*

fun main() {
    runBlocking {
        SuspendFun1.f2()
        println()
    }
}

object SuspendFun1 {
    suspend fun f2() {
        //Breakpoint!
        yield()
        println()
    }
}
