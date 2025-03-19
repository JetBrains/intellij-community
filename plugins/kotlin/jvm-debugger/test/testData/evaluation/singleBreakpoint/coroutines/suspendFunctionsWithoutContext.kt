package suspendFunctionsWithoutContext

// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2)

import kotlinx.coroutines.yield

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

suspend fun main(args: Array<String>) {
    // EXPRESSION: one() + two() + three()
    // RESULT: 6: I
    //Breakpoint!
    println("")
}
