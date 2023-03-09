// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.core.stackFrame

import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl
import com.sun.jdi.ObjectReference
import com.sun.jdi.StackFrame

abstract class KotlinStackFrameProxyImpl(
    threadProxy: ThreadReferenceProxyImpl,
    stackFrame: StackFrame,
    indexFromBottom: Int
) : StackFrameProxyImpl(threadProxy, stackFrame, indexFromBottom) {
    /**
     * For suspend lambdas, the stack frame proxy `thisObject` is sometimes the `CoroutineScope`
     * instead of the actual lambda implementation. Therefore, for Kotlin we need a way to
     * consistently get the dispatch receiver in order to get the captures for suspend lambdas.
     */
    abstract fun dispatchReceiver(): ObjectReference?
}