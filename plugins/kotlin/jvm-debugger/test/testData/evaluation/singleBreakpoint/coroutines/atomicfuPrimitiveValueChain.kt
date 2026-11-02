package atomicfuPrimitiveValueChain

// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.10.2.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:atomicfu-jvm-0.27.0.jar)

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

// IDEA-335346 (#3, primitive-backed). `_decisionAndIndex` is an erased `AtomicInt`; reading it unwraps
// to the int field, but the preceding CHECKCAST to `AtomicInt` boxes it, so a following `.value`
// (getValue()I) must unbox the receiver or the interpreter reads an ObjectValue where it expects an int.
// Compared against itself so the result is stable regardless of the coroutines-internal decision value;
// the point is that the primitive `.value` chain evaluates at all (it used to throw ClassCastException).
fun main() = runBlocking {
    var saved: CancellableContinuation<*>? = null
    suspendCancellableCoroutine<Unit> { cont ->
        saved = cont
        cont.resume(Unit)
    }
    // EXPRESSION: (saved as kotlinx.coroutines.CancellableContinuationImpl<*>)._decisionAndIndex.value == (saved as kotlinx.coroutines.CancellableContinuationImpl<*>)._decisionAndIndex.value
    // RESULT: 1: Z
    //Breakpoint!
    println(saved)
}
