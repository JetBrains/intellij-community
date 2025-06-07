package optimisedVariablesInSuspendInline

// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2)
// SHOW_KOTLIN_VARIABLES

import kotlinx.coroutines.*

suspend inline fun foo() {
    val a = "a"
    inlineBlock {
        val b = "b"
        //Breakpoint!
        delay(1)
        //Breakpoint!
        suspendUse(b)
    }
    suspendUse(a)
    //Breakpoint!
    println("")
}

fun main() = runBlocking {
    foo()
}

suspend fun suspendUse(value: Any) {
    delay(1)
}

inline fun inlineBlock(block: () -> Unit) = block()
