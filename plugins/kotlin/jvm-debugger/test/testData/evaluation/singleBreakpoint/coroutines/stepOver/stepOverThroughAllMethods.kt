// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3)-javaagent

import kotlinx.coroutines.*

fun main() {
    runBlocking {
        for (i in 1 .. 100) {
            launch(Dispatchers.Default) {
                aaa(i)
                val result1 = bar(true)
                val result2 = bar(false)
                if (i == 25) {
                    println("after a: $i")
                }
            }
        }
    }
}

suspend fun aaa(i: Int) {
    beforeBBB(i)
    bbb(i)
    afterBBB(i)
}

fun beforeBBB(i: Int) {
    println("beforeBBB: $i")
}

suspend fun afterBBB(i: Int) {
    delay(1)
    println("afterBBB: $i")
}

suspend fun bbb(i: Int) {
    delay(1)
    ccc(i)
    delay(1)
    println("End bbb")
}

fun ccc(i: Int) {
    var cInt = 0
    if (i == 25) {
        //Breakpoint!
        cInt = i
    }
    val cStr = "hello $cInt"
    println("ccc: end $cInt, $cStr")
}

private suspend fun bar(willSuspend: Boolean): String {
    if (willSuspend) delay(1)
    return "Result"
}

// STEP_OVER: 12