package coroutinesView

// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2)-javaagent

import kotlinx.coroutines.*

fun main() {
    runBlocking(CoroutineName("root")) {
        a()
    }
}

suspend fun a() {
    val a = "a"
    b(a)
    val aLate = a
}

suspend fun b(paramA: String) {
    yield()
    val b = "b"
    c(b)
    val dead = paramA
}

suspend fun c(paramB: String) {
    val c = "c"
    //Breakpoint!
    val dead = paramB
}