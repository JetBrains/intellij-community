// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.6.4.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.6.4.jar)
// REGISTRY: debugger.emulate.method.breakpoints=false

package functionBreakpointPassCountInSuspendFunNotEmulated

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

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
    yield()
    yield()
    return n * 10
}
