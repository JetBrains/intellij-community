// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3)-javaagent
// REGISTRY: debugger.async.stacks.coroutines=false



import kotlinx.coroutines.*

fun main() = runBlocking {
    for (i in 0 .. 100) {
        launch(Dispatchers.Default) {
            foo(i)
        }
    }
    println()
}

suspend fun foo(i: Int) {
    println()
    withContext(Dispatchers.IO) {
        if (i == 25) {
            //Breakpoint!
            i.toString()
        }
        bar(5, 7);
        delay(10);
        i.toString()
    } // the last step over is a step out
    delay(10)
    i.toString()
}

suspend fun bar(x: Int, y: Int) {
    delay(10)
    x.toString()
    y.toString()
    delay(10)
}

// STEP_OVER: 5

// EXPRESSION: i
// RESULT: 25: I