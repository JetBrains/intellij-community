// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.6.4.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.6.4.jar)

package functionBreakpointPassCountInSuspendFunDefaultArg

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

suspend fun main() {
    coroutineScope {
        launch {
            println(foo(1))
            println(foo())
            println(foo(3))
        }
    }
}

// The second call goes through foo$default.
// EXPRESSION: n
// RESULT: 0: I
//FunctionBreakpoint! passCount = 2
private suspend fun foo(n: Int = 0): Int {
    yield()
    yield()
    return n * 10
}
