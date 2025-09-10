package continuation
// ATTACH_JAVA_AGENT_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-debug-1.3.8.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.3.8.jar)
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
