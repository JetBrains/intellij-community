// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3)-javaagent

import kotlinx.coroutines.*

fun main() = runBlocking<Unit> {
    //Breakpoint!
    println("Start")
    val a = async {
        println("I'm computing a piece of the answer")
        6
    }
    val b = async {
        println("I'm computing another piece of the answer")
        7
    }
    println("The answer is ${a.await() * b.await()}")
    println("End")
}

// STEP_OVER: 6