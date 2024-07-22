// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3)-javaagent

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking

fun numbers(): Flow<Int> = flow {
    try {
        //Breakpoint!
        emit(1)
        emit(2)
        emit(3)
        emit(4)
        emit(5)
        println("This line will not execute")
        emit(3)
    } finally {
        println("Finally in numbers")
    }
}

fun main() = runBlocking<Unit> {
    numbers()
        .take(4)
        .collect { value ->
            //Breakpoint!
            println(value)
        }
}

// STEP_OVER: 18
