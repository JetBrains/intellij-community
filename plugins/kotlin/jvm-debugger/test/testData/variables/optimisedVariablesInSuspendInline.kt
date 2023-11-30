package optimisedVariablesInSuspendInline

// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2)
// SHOW_KOTLIN_VARIABLES

import kotlinx.coroutines.runBlocking

suspend inline fun foo() {
    val a = "a"
    //Breakpoint!
    inlineBlock {
        val b = "b"
        //Breakpoint!
        suspendUse(b)
    }
    //Breakpoint!
    suspendUse(a)
    //Breakpoint!
    println("")
}

fun main() = runBlocking {
    foo()
}

suspend fun suspendUse(value: Any) {

}

inline fun inlineBlock(block: () -> Unit) = block()
