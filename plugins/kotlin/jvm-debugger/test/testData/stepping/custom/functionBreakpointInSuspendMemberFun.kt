// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.6.4.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.6.4.jar)
// REGISTRY: debugger.method.breakpoints.entry.default=false

package functionBreakpointInSuspendMemberFun

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

suspend fun main() {
    coroutineScope {
        launch {
            val r = A().foo()
            println(r)
        }
    }
}

class A {
    //FunctionBreakpoint!
    suspend fun foo(): Int {
        yield()
        yield()
        return 37
    }
}
