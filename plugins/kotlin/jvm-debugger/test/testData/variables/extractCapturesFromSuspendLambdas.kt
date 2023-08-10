package nestedInlineFunctions

// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2)
// SHOW_KOTLIN_VARIABLES

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

fun runBlocking() {
    val x = 32
    runBlocking {
        //Breakpoint!
        println(x)
    }
}

suspend fun suspendLambda() {
    val x = 32
    runner {
        //Breakpoint!
        println(x)
    }
}

suspend fun suspendInlineLambda() {
    val x = 32
    inlinedRunner {
        //Breakpoint!
        println(x)
    }
}

suspend fun withContext() {
    val x = 32
    withContext(Dispatchers.IO) {
        //Breakpoint!
        println(x)
    }
}

suspend fun suspendLambdaWithContext() {
    val x = 32
    runner {
        withContext(Dispatchers.IO) {
            //Breakpoint!
            println(x)
        }
    }
}

fun complex() {
    val x1 = "X1"
    runBlocking {
        val x2 = "X2"
        withContext(Dispatchers.IO) {
            val x3 = "X3"
            runner {
                val x4 = "X4"
                withContext(Dispatchers.IO) {
                    val x5 = "X5"
                    //Breakpoint!
                    println("$x1-$x2-$x3-$x4-$x5")
                }
            }
        }

    }
}

suspend fun runner(f: suspend () -> Unit) = f()

suspend inline fun inlinedRunner(crossinline block: suspend () -> Unit) = withContext(Dispatchers.IO) { block() }

suspend fun main() {
    runBlocking()
    withContext()
    suspendLambda()
    suspendInlineLambda()
    suspendLambdaWithContext()
    complex()
}

// IGNORE_K2