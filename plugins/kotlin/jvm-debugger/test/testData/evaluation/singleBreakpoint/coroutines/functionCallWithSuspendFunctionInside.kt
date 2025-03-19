package functionCallWithSuspendFunctionInside

// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2)

import kotlinx.coroutines.yield

suspend fun one(): Int {
    yield()
    return 1
}

fun main() {
    //Breakpoint!
    println("")
}

// IGNORE_K2