package optimisedVariablesWithLambdas

// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2)
// SHOW_KOTLIN_VARIABLES

import kotlinx.coroutines.runBlocking

suspend fun foo(param1: Int, param2: Int, param3: Int) {
    val a = "a"
    //Breakpoint!
    suspendUse(a)
    val b = "b"
    //Breakpoint!
    suspendUse(b)
    suspendUse(param1)
    //Breakpoint!
    block {
        val c = "c"
        //Breakpoint!
        println("")
        val d = "d"
        //Breakpoint!
        println("")
    }

    suspendBlock {
        val e = "e"
        //Breakpoint!
        suspendUse(e)
        val f = "f"
        //Breakpoint!
        suspendUse(f)
        //Breakpoint!
    }

    inlineBlock {
        val g = "g"
        //Breakpoint!
        suspendUse(g)

        block {
            val h = "h"
            //Breakpoint!
            println("")
        }

        suspendUse(param2)

        inlineBlock {
            val i = "i"
            //Breakpoint!
            suspendUse(i)
            suspendUse(param3)
        }

        val j = "j"
        //Breakpoint!
        suspendUse(j)
    }

    suspendUse(a)

    noinlineBlock {
        val k = "k"
        //Breakpoint!
        println("")
    }

    //Breakpoint!
    println("")
    //Breakpoint!
}

fun main() = runBlocking {
    foo(1, 2, 3)
}

suspend fun suspendUse(value: Any) {

}

inline fun inlineBlock(block: () -> Unit) = block()

inline fun noinlineBlock(noinline block: () -> Unit) = block()

fun block(block: () -> Unit) = block()

suspend fun suspendBlock(block: suspend () -> Unit) = block()
