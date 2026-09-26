// ATTACH_LIBRARY: coroutines

package oneLineSuspendFunWithInline

import forTests.builder

inline fun inlineFun(f: () -> Int): Int? {
    val a = 1
    val b = 2
    return a + b + f()
}

suspend fun suspendFun(): Int = inlineFun { 1 }?.let { return it } ?: 0

fun main() {
    builder {
        //Breakpoint!
        suspendFun()
    }
}

// STEP_INTO: 1
