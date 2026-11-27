// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.10.2.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.10.2.jar)

package functionBreakpointPassCountInNonSuspendingSuspendFun

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

suspend fun main() {
    coroutineScope {
        launch {
            for (n in 1..3) println(foo(n))
        }
    }
}

// EXPRESSION: n
// RESULT: 2: I
//FunctionBreakpoint! passCount = 2
private suspend fun foo(n: Int): Int {
    return n * 10
}
