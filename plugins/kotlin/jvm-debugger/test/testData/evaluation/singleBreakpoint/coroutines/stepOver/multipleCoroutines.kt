// ATTACH_JAVA_AGENT_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.7.3.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.7.3.jar)

import kotlinx.coroutines.*



private fun CoroutineScope.work(i: Int) {
    launch(Dispatchers.Default) {
        if (i == 5) {
            //Breakpoint!
            startMethod(i)
        }
        delay(1)
        endMethod(i)
        // EXPRESSION: i
        // RESULT: 5: I
        println(i)
    }
}

suspend fun startMethod(i: Int) {
    if (i == 5) {
        delay(1)
        "".toString()
    }
}

suspend fun endMethod(i: Int) {
    delay(1)
    println("End")
}

fun main() {
    runBlocking {
        coroutineScope {
            work(-1)
        }
        for (i in 1 .. 100) {
            work(i)
        }
    }
}

// STEP_OVER: 4