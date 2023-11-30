package optimisedVariablesInNestedFunctions

// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2)
// SHOW_KOTLIN_VARIABLES

import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    fun foo(p: Int) {
        val a = 1;
        //Breakpoint!
        println("")
    }

    suspend fun suspendFoo(p: Int) {
        val a = 1;
        //Breakpoint!
        suspendUse(p)
        //Breakpoint!
        suspendUse(a)
        //Breakpoint!
        println("")
    }

    fun bar(p: Int) =
        //Breakpoint!
        when (val x = 1) {
            is Int -> {
                //Breakpoint!
                println("")
            }
            else -> {}
        }

    suspend fun suspendBar(p: Int) =
        //Breakpoint!
        when (val x = 1) {
            is Int -> {
                //Breakpoint!
                suspendUse(p)
                //Breakpoint!
                suspendUse(x)
                //Breakpoint!
                println("")
            }
            else -> {}
        }

    foo(1)
    suspendFoo(1)
    bar(1)
    suspendBar(1)
}

suspend fun suspendUse(value: Any) {

}

