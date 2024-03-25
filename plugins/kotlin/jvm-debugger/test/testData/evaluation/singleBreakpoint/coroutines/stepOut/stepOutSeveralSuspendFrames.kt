// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3)-javaagent

package souSuspendFun

import kotlinx.coroutines.*

private fun foo(): Int {
    //Breakpoint!
    return 42                                                      
}

suspend fun fourth(): Int {
    delay(1)
    foo()
    return 5
}

suspend fun third() : Int? {
    delay(1)
    val res = fourth()
    println(res)
    return res                                 
}

suspend fun second(): Int {
    delay(1)
    return third()?.let { return it } ?: 0
}      

suspend fun first(): Int {
    delay(1)
    second()                                                      
    return 12
}

fun main() {
    runBlocking {
        launch {
            first()                                                  
        }
    }
}

// STEP_OUT: 5

// REGISTRY: debugger.filter.breakpoints.by.coroutine.id=true
