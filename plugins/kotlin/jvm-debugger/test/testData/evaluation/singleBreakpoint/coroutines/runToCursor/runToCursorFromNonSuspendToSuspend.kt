// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3)-javaagent

import kotlinx.coroutines.*

fun main() {
    runBlocking {
        for (i in 1 .. 100) {
            launch(Dispatchers.Default) {
                aaa(i)
                // EXPRESSION: i
                // RESULT: 5: I
                // RUN_TO_CURSOR: 1
                println("after a: $i")
            }
        }
    }
}

suspend fun aaa(i: Int) {
    beforeBBB(i)
    if (i == 5) {
        bbb(i)
    }
    afterBBB(i)
}

fun beforeBBB(i: Int) {
    println("beforeBBB: $i")
}

fun afterBBB(i: Int) {
    println("afterBBB: $i")
}

suspend fun bbb(i: Int) {
    delay(1)
    ccc(i)
    delay(1)
    println("End bbb")
}

fun ccc(i: Int) {
    //Breakpoint!
    val cInt = i
    val cStr = "hello $cInt"
    println("ccc: end $cInt, $cStr")
}
