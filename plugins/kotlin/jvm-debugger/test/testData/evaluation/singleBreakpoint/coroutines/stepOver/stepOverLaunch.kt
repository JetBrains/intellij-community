// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3)-javaagent

import kotlinx.coroutines.*

fun main() {
    runBlocking {
        //Breakpoint!
        println("Start")
        launch {
            println("main runBlocking      : I'm working in thread ${Thread.currentThread().name}")
        }
        launch(Dispatchers.Unconfined) {
            println("Unconfined            : I'm working in thread ${Thread.currentThread().name}")
        }
        launch(Dispatchers.Default) {
            println("Default               : I'm working in thread ${Thread.currentThread().name}")
        }
        launch(newSingleThreadContext("MyOwnThread")) {
            println("newSingleThreadContext: I'm working in thread ${Thread.currentThread().name}")
        }
        println("End")
    }
}

// STEP_OVER: 5
// REGISTRY: debugger.filter.breakpoints.by.coroutine.id=true