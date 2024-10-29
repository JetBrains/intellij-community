package coroutineContextExists

// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2)

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineName

fun main() = runBlocking(CoroutineName("NAME")) {
    // EXPRESSION: kotlin.coroutines.coroutineContext[CoroutineName.Key]?.name
    // RESULT: "NAME": Ljava/lang/String;
    //Breakpoint!
    println("")
}
