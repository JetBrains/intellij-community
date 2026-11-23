// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.6.4.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.6.4.jar)

package functionBreakpointPassCountAndConditionInSuspendFun

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

suspend fun main() {
    coroutineScope {
        launch {
            for (n in 1..5) println(foo(n))
        }
    }
}

// The pass count applies to the hits that pass the condition: n = 1, 3, 5.
// EXPRESSION: n
// RESULT: 3: I
//FunctionBreakpoint! condition = n % 2 == 1, passCount = 2
private suspend fun foo(n: Int): Int {
    yield()
    yield()
    return n * 10
}
