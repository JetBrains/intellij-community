// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3)-javaagent

package stepOutNoSuspension

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private fun foo(): Int {
    //Breakpoint!
    return 42
}

suspend fun fourth(): Int {
    foo()
    return 5
}

suspend fun third() : Int? {
    fourth()
    delay(10)
    return 5
}

suspend fun second(): Int = third()?.let { return it } ?: 0

suspend fun first(): Int {
    second()
    return 12
}

fun main() {
    runBlocking {
        launch {
            first()
        }
    }
}

// STEP_OUT: 5