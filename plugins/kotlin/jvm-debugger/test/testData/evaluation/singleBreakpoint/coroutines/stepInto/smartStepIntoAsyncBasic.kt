// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3)-javaagent
// REGISTRY: debugger.filter.breakpoints.by.coroutine.id=true

import kotlinx.coroutines.*

fun main() {
    runBlocking {
        for (i in 0 .. 100) {
            if (i == 34) {
                //Breakpoint!
                "".toString()
            }
            val res = async {
                delay(10)
                delay(10)
                "After delay $i"
            }
            res.join()
            println("Obtained result $res")
            i.toString()
        }
    }
}

// STEP_OVER: 1
// SMART_STEP_INTO_BY_INDEX: 1
// STEP_OVER: 3