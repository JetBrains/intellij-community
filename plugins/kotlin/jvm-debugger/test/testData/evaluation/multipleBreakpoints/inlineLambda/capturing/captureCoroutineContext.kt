// IGNORE_K1

import kotlin.coroutines.coroutineContext

suspend fun main() {
    foo { coroutineContext.toString() }
}

inline suspend fun foo(block: () -> String) {
    // EXPRESSION: block()
    // RESULT: "EmptyCoroutineContext": Ljava/lang/String;
    //Breakpoint!
    bar { block() + "_" + coroutineContext.toString() }
}

inline suspend fun bar(block: () -> String) {
    // EXPRESSION: block()
    // RESULT: "EmptyCoroutineContext_EmptyCoroutineContext": Ljava/lang/String;
    //Breakpoint!
    val x = 1
}