package coroutineContextExists

// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.4.2.jar)

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineName

fun main() = runBlocking(CoroutineName("NAME")) {
    // EXPRESSION: kotlin.coroutines.coroutineContext[CoroutineName.Key]?.name
    // RESULT: "NAME": Ljava/lang/String;
    //Breakpoint!
    println("")
}
