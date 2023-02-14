package threeCoroutines

// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-debug:1.3.8)-javaagent

import kotlin.random.Random

suspend fun main() {
    sequence {
        yield(239)
        sequence {
            //Breakpoint!
            yield(666)
        }.toList()
    }.toList()
}
