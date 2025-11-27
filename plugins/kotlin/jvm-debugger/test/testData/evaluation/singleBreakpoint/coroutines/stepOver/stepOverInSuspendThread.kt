// USE_XSESSION_PAUSE_LISTENER
// ATTACH_JAVA_AGENT_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.7.3.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.7.3.jar)

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex

fun main() {
    val mutex = Mutex(true)

    suspend fun megaUnlock() {
        delay(500)
        mutex.unlock()
        delay(500)
    }

    runBlocking {
        launch(Dispatchers.Default) {
            for (y in 0..100) {
                launch(Dispatchers.Default) {
                    for (x in 0..100) {
                        if (y == 13 && x == 0) {
                            //Breakpoint!, suspendPolicy = SuspendThread
                            megaUnlock()
                            delay(500)
                            y.toString()
                            "".toString()
                        }
                        if (y == 77 && x == 0) {
                            mutex.lock()
                            //Breakpoint!, suspendPolicy = SuspendThread
                            y.toString()
                            "".toString()
                            "".toString()
                            "".toString()
                        }
                        delay(10)
                    }
                }
            }
        }
    }
}

// STEP_OVER: 2
// RESUME: 1
// STEP_OVER: 2
