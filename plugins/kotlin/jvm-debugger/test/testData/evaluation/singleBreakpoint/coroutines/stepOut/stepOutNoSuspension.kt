// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3)-javaagent
// REGISTRY: debugger.async.stacks.coroutines=false



package souSuspendFun

import kotlinx.coroutines.*

private fun foo(): Int {
    //Breakpoint!
    return 42                                                      // 1
}

// One line suspend wihtout doResume
suspend fun fourth() = foo()                                       // 2

// Multiline suspend without doResume
suspend fun third() : Int? {
    return fourth() * 10                                         // 3
}

// One line suspend with doResume
suspend fun second(): Int {
    return third()?.let { return it } ?: 0
}

// Multiline suspend with doResume
suspend fun first(): Int {
    second()                                                       // 5
    return 12
}

fun main() = runBlocking {
    launch {
        first()                                                    // 6
    }
    println("End")
}

// STEP_OUT: 5