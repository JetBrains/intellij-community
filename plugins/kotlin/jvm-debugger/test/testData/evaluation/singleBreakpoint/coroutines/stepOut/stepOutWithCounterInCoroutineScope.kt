// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3)-javaagent
// REGISTRY: debugger.async.stacks.coroutines=false



import kotlinx.coroutines.*

fun main() = runBlocking {
    coroutineScope {
        for (y in 0..100) {
            launch(Dispatchers.Default) {
                var x = 0
                for (i in 0 .. 10) {
                    foo(x, y)
                    x++
                }
            }
        }
    }
    println()
}

suspend fun foo(x: Int, y: Int) {
    delay(10)
    x.toString()
    if (y == 25) {
        //Breakpoint!
        bar(x, y)
    }
    bar(x, y)
    delay(10)
}

suspend fun bar(x: Int, y: Int) {
    delay(1)
    x.toString()
    y.toString()
    delay(1)
}

// STEP_OUT: 1

// STEP_OVER: 3
// STEP_OUT: 1

// STEP_OVER: 3
// STEP_OUT: 1

// STEP_OVER: 3
// STEP_OUT: 1

// STEP_OVER: 3
// STEP_OUT: 1

// EXPRESSION: x
// RESULT: 4: I

// EXPRESSION: y
// RESULT: 25: I





