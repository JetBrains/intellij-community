package atomicfuIsCheck

// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.10.2.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:atomicfu-jvm-0.27.0.jar)

import kotlinx.coroutines.runBlocking

// IDEA-335346: the atomicfu holder type is erased at runtime, so the evaluator lets a CHECKCAST to it
// pass as a no-op (the value is already unwrapped). That must NOT leak into `is` / INSTANCEOF: an
// arbitrary value that is not an atomic has to test false. Regression for a false positive where
// `<anything> is kotlinx.atomicfu.AtomicInt` always yielded true in the debugger.
fun main() = runBlocking {
    val value: Any = "not an atomic"
    // EXPRESSION: value is kotlinx.atomicfu.AtomicInt
    // RESULT: 0: Z
    //Breakpoint!
    println(value)
}
