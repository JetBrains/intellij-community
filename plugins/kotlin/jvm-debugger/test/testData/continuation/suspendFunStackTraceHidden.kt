package continuation
// ATTACH_JAVA_AGENT_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.10.2.jar)
// JVM_SYSTEM_PROPERTY: kotlinx.coroutines.debug.enable.creation.stack.trace=true
// SHOW_LIBRARY_STACK_FRAMES: false
// REGISTRY: debugger.library.frames.fold.instead.of.hide=false
// REGISTRY: debugger.async.stacks.coroutines=false

import kotlinx.coroutines.runBlocking

fun main() {
    runBlocking {
        //Breakpoint!
        println("inside coroutine")
    }
}
