package coroutineRunningOnSuspendedThread

// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2)

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

suspend fun contextName() = coroutineContext[CoroutineName.Key]?.name

fun main(args: Array<String>) = runBlocking<Unit> {
    launch(CoroutineName("A")) {
        // EXPRESSION: contextName()
        // RESULT: "A": Ljava/lang/String;
        //Breakpoint!
        println("")

        launch(CoroutineName("B")) {
            // EXPRESSION: contextName()
            // RESULT: "B": Ljava/lang/String;
            //Breakpoint!
            println("")

            launch(CoroutineName("C")) {
                // EXPRESSION: contextName()
                // RESULT: "C": Ljava/lang/String;
                //Breakpoint!
                println("")
            }
        }
    }
}
