package suspendFunctionsWithContext

// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2)

import kotlinx.coroutines.yield
import kotlinx.coroutines.runBlocking

suspend fun one(): Int {
    yield()
    return 1
}

suspend fun two(): Int {
    yield()
    return 2
}

suspend fun three(): Int {
    yield()
    return 3
}

fun main(args: Array<String>) = runBlocking {
    //Breakpoint!
    println("")
}

// EXPRESSION: one() + two() + three()
// RESULT: 6: I

// EXPRESSION: one(); 41 + 1
// RESULT: 42: I
