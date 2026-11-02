package atomicfuVolatileField

// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.10.2.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:atomicfu-jvm-0.27.0.jar)

import kotlinx.coroutines.runBlocking

// IDEA-335346 (#2): kotlinx.atomicfu renames JobSupport's field `_state` to `_state$volatile` at runtime.
// The source name `_state` compiles to GETFIELD `_state`, absent at runtime, so it throws
// NoSuchFieldException until the debugger maps the name.
// `this` in runBlocking is the CoroutineScope, a JobSupport subtype at runtime.
fun main() = runBlocking {
    // EXPRESSION: (this as kotlinx.coroutines.JobSupport)._state != null
    // RESULT: 1: Z
    //Breakpoint!
    println("")
}
