package functionCallWithSuspendFunctionInside

// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.10.2.jar)

import kotlinx.coroutines.yield

suspend fun one(): Int {
    yield()
    return 1
}

fun main() {
    //Breakpoint!
    println("")
}

// IGNORE_K2