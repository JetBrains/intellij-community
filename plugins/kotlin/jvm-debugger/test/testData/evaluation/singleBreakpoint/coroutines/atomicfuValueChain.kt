package atomicfuValueChain

// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.10.2.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:atomicfu-jvm-0.27.0.jar)

import kotlinx.coroutines.runBlocking

// IDEA-335346 (#3): a call chain AFTER an atomicfu property. `_state` resolves to the erased backing
// value, and the following `.value` read used to throw because that unwrapped value has no `getValue`.
// The evaluator now maps the holder's `.value` getter to the already-unwrapped receiver (identity).
fun main() = runBlocking {
    // EXPRESSION: (this as kotlinx.coroutines.JobSupport)._state.value != null
    // RESULT: 1: Z
    //Breakpoint!
    println("")
}
