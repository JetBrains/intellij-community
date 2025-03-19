package spilledCapturedVariables

// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2)
// SHOW_KOTLIN_VARIABLES

import kotlinx.coroutines.runBlocking

class A {
    suspend inline fun foo() {
        val x = 1
        inlineBlock {
            val y = 1
            inlineBlock {
                val z = 1
                //Breakpoint!
                suspendUse(z)
            }
            //Breakpoint!
            suspendUse(y)
        }
        //Breakpoint!
        suspendUse(x)
    }
}

fun main() = runBlocking {
    A().foo()
}

suspend fun suspendUse(value: Any) {
}

inline fun inlineBlock(block: () -> Unit) = block()
