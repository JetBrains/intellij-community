package continuation

// ATTACH_JAVA_AGENT_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.10.2.jar)
// JVM_SYSTEM_PROPERTY: kotlinx.coroutines.debug.enable.creation.stack.trace=true
// REGISTRY: debugger.async.stacks.coroutines=false

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

suspend fun main() = coroutineScope {
    launch {
        delay(1000)
        println("bye")
    }
    //Breakpoint!
    println("hello")
}