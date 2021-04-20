package uncapturedVariableInSuspendLambda

// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2)

import kotlinx.coroutines.runBlocking


fun main(args: Array<String>) {
    val notUsedInside = "not used inside"
    val usedInside = "used inside"
    runBlocking {
        // EXPRESSION: usedInside
        // RESULT: "used inside": Ljava/lang/String;
        // EXPRESSION: notUsedInside
        // RESULT: 'notUsedInside' is not captured
        //Breakpoint!
        println(usedInside)
    }
}