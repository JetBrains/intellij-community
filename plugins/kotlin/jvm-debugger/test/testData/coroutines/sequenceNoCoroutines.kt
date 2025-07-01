package threeCoroutines

// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2)-javaagent

import kotlin.random.Random

fun main() {
    sequence {
        yield(239)
        sequence {
            //Breakpoint!
            yield(666)
        }.toList()
    }.toList()
}