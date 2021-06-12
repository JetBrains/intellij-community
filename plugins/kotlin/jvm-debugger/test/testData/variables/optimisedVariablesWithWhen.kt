package optimisedVariablesWithWhen

// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2)
// SHOW_KOTLIN_VARIABLES

import kotlinx.coroutines.runBlocking

suspend fun foo() {
    val a = "a"
    //Breakpoint!
    when (val b = "b") {
        is String -> {
            val c = "c"
            //Breakpoint!
            suspendUse(c)
            block {
                val d = "d"
                //Breakpoint!
                println("")
            }
            
            inlineBlock {
                val e = "e"
                //Breakpoint!
                suspendUse(e)
            }
        
            when (val f = "f") {
                is String -> {
                    val g = "g"
                    //Breakpoint!
                    suspendUse(g)
                    block {
                        val h = "h"
                        //Breakpoint!
                        println("")
                    }
            
                    inlineBlock {
                        val i = "i"
                        //Breakpoint!
                        suspendUse(i)
                    }

                    suspendUse(f)
                    //Breakpoint!
                    println("")
                }
                else -> {}
            }

            suspend fun foo() {
                val x = "x"
                //Breakpoint!
                suspendUse(x)
                val y = "y"
                //Breakpoint!
                suspendUse(y)
                //Breakpoint!
                println("")
            }

            suspendUse(b)
            foo()
            //Breakpoint!
            println("")
        }
        else -> {}
    }

    //Breakpoint!
    suspendUse(a)

    when {
        else -> {
            //Breakpoint!
            println("")
        }
    }
}

fun main() = runBlocking {
    foo()
}

suspend fun suspendUse(value: Any) {

}

inline fun inlineBlock(block: () -> Unit) = block()

fun block(block: () -> Unit) = block()
