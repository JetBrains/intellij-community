package coroutinesView

// ATTACH_JAVA_AGENT_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.10.2.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.10.2.jar)

import kotlinx.coroutines.*

fun main() {
    runBlocking(CoroutineName("root")) {
        coroutineScope {
            val a = async(CoroutineName("CoroutineA")) {
                delay(10)
                val b = async(CoroutineName("ChildCoroutineB")) {
                    val coroutineVar = 2
                    delay(1000)
                    "b"
                }
                delay(5)
                "a" + b.await()
            }
            delay(100)
            val c = async(CoroutineName("CoroutineC")) {
                delay(10)
                val d = async(CoroutineName("ChildCoroutineD")) {
                    delay(100)
                    "d"
                }
                //Breakpoint!
                "c" + d.await()
            }
        }
        launch(CoroutineName("Was not launched yet")) {
            delay(100)
        }
    }
}