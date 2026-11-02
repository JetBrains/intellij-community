// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.10.2.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:atomicfu-jvm-0.27.0.jar)

import kotlinx.coroutines.Job

fun main() {
    val job = Job()
    //Breakpoint!
    println()
}

// IDEA-335346 (#1b): the Evaluate prefill for the atomicfu-renamed field `_state$volatile` uses the
// source name `_state`, so the prefilled expression resolves.
// VARIABLE_PATH: job._state$volatile
// EVAL_EXPRESSION: (job as JobImpl)._state
