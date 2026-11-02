package atomicfuPrimitiveField

// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.10.2.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:atomicfu-jvm-0.27.0.jar)

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

// IDEA-335346 (#2): same atomicfu field-rename, but for a PRIMITIVE-backed atomic.
// CancellableContinuationImpl has `_decisionAndIndex` (atomic<Int>), erased to the field
// `_decisionAndIndex$volatile : int`. Evaluating `_decisionAndIndex` must read that int field.
//
// The continuation is captured into `saved` (public `CancellableContinuation`, the impl type is internal)
// and read after the suspension point, so the variable stays live under inline scopes too: the lambda
// parameter `cont` would be optimized away there.
fun main() = runBlocking {
    var saved: CancellableContinuation<*>? = null
    suspendCancellableCoroutine<Unit> { cont ->
        saved = cont
        cont.resume(Unit)
    }
    // EXPRESSION: (saved as kotlinx.coroutines.CancellableContinuationImpl<*>)._decisionAndIndex
    // Primitive-backed atomic (AtomicInt): the value is read correctly but comes back BOXED as
    // java.lang.Integer, because the fragment types the expression as the reference `AtomicInt`.
    // A clean `int` would require typing it as `Int` at the fragment-resolution level. See IDEA-335346.
    // RESULT: instance of java.lang.Integer(id=ID): Ljava/lang/Integer;
    //Breakpoint!
    println(saved)
}
