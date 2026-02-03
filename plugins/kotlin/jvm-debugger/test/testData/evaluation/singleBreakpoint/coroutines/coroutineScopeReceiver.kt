package coroutineScopeReceiver

// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.4.2.jar)

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

fun CoroutineScope.text() = "TEXT"

suspend fun CoroutineScope.suspendText() = "SUSPEND TEXT"

fun main(args: Array<String>) = runBlocking {
    // EXPRESSION: text()
    // RESULT: "TEXT": Ljava/lang/String;
    // EXPRESSION: suspendText()
    // RESULT: "SUSPEND TEXT": Ljava/lang/String;
    //Breakpoint!
    println("")
}
