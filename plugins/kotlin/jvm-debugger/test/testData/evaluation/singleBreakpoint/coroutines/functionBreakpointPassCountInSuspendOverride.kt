// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.10.2.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.10.2.jar)

package functionBreakpointPassCountInSuspendOverride

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

interface Api {
    suspend fun foo(n: Int): Int
}

class Impl : Api {
    // EXPRESSION: n
    // RESULT: 2: I
    //FunctionBreakpoint! passCount = 2
    override suspend fun foo(n: Int): Int {
        yield()
        yield()
        return n * 10
    }
}

suspend fun main() {
    val api: Api = Impl()
    coroutineScope {
        launch {
            for (n in 1..3) println(api.foo(n))
        }
    }
}
