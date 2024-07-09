// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3)-javaagent

import kotlinx.coroutines.*

suspend fun foo(i: Int) {
    println("Start foo")
    coroutineScope {
        trackExecution("Start for $i")
        if (i == 25) {
            //Breakpoint!
            startMethod(i)
        }
        trackExecution("Middle for $i")
        delay(1)
        // EXPRESSION: i
        // RESULT: 25: I
        println("After delay $i")
    }
    println("coroutineScope completed $i")
}

suspend fun startMethod(i: Int) {
    if (i == 25) {
        delay(1)
        "".toString()
    }
}

suspend fun endMethod(i: Int) {
    delay(1)
}

fun main() {
    runBlocking {
        foo(-1)
        repeat(100) { i ->
            launch(Dispatchers.Default) {
                foo(i)
            }
        }
    }
}

fun trackExecution(s: String) {
    //LogFirstArgumentBreakpoint!
    s.toString()
}


// STEP_OVER: 4
// REGISTRY: debugger.filter.breakpoints.by.coroutine.id=true
// REGISTRY: debugger.always.suspend.thread.before.switch=true
// REGISTRY: debugger.log.jdi.in.unit.tests=true
// REGISTRY: debugger.how.to.switch.to.suspend.all=[IMMEDIATE_PAUSE*]
