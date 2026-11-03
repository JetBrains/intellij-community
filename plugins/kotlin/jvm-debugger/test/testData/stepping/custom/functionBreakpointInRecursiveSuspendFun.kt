// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.6.4.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.6.4.jar)
// REGISTRY: debugger.method.breakpoints.entry.default=false

package functionBreakpointInRecursiveSuspendFun

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

suspend fun main() {
    coroutineScope {
        launch {
            val r = rec(2)
            println(r)
        }
    }
}

//FunctionBreakpoint!
private suspend fun rec(n: Int): Int {
    yield()
    if (n > 0) return rec(n - 1) + 1
    return 0
}
