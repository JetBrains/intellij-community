// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.coroutine.proxy

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl
import com.sun.jdi.Location
import com.sun.jdi.ObjectReference
import com.sun.jdi.StackFrame
import com.sun.jdi.Value
import org.jetbrains.kotlin.idea.debugger.coroutine.data.ContinuationVariableValueDescriptorImpl
import org.jetbrains.kotlin.idea.debugger.safeStackFrame
import org.jetbrains.kotlin.idea.debugger.safeThreadProxy
import org.jetbrains.kotlin.idea.debugger.wrapEvaluateException

class SkipCoroutineStackFrameProxyImpl(
    threadProxy: ThreadReferenceProxyImpl,
    stackFrame: StackFrame,
    indexFromBottom: Int
) : StackFrameProxyImpl(threadProxy, stackFrame, indexFromBottom)

class CoroutineStackFrameProxyImpl(
    private val location: Location?,
    val spilledVariables: List<JavaValue>,
    threadProxy: ThreadReferenceProxyImpl,
    stackFrame: StackFrame,
    indexFromBottom: Int
) : StackFrameProxyImpl(threadProxy, stackFrame, indexFromBottom) {
    val continuation = wrapEvaluateException { super.thisObject() }
    private val coroutineScope by lazy { extractCoroutineScope() }

    fun updateSpilledVariableValue(name: String, value: Value?) {
        val descriptor = spilledVariables.find { it.name == name }?.descriptor as? ContinuationVariableValueDescriptorImpl ?: return
        descriptor.updateValue(value)
    }

    fun isCoroutineScopeAvailable() = coroutineScope != null

    override fun location(): Location? =
        location

    override fun thisObject() =
        coroutineScope ?: continuation

    private fun extractCoroutineScope(): ObjectReference? {
        if (continuation == null) {
            return null
        }

        val debugProcess = virtualMachine.debugProcess as? DebugProcessImpl ?: return null
        val suspendContext = debugProcess.suspendManager.pausedContext ?: return null
        val evaluationContext = EvaluationContextImpl(suspendContext, this)
        return CoroutineScopeExtractor.extractCoroutineScope(continuation, evaluationContext)
    }
}

fun safeSkipCoroutineStackFrameProxy(frameProxy: StackFrameProxyImpl): StackFrameProxyImpl {
    val threadProxy = frameProxy.safeThreadProxy() ?: return frameProxy
    val stackFrame = frameProxy.safeStackFrame() ?: return frameProxy
    return SkipCoroutineStackFrameProxyImpl(threadProxy, stackFrame, frameProxy.indexFromBottom)
}

fun safeCoroutineStackFrameProxy(
    location: Location?,
    spilledVariables: List<JavaValue>,
    frameProxy: StackFrameProxyImpl
): StackFrameProxyImpl {
    val threadProxy = frameProxy.safeThreadProxy() ?: return frameProxy
    val stackFrame = frameProxy.safeStackFrame() ?: return frameProxy
    return CoroutineStackFrameProxyImpl(
        location,
        spilledVariables,
        threadProxy,
        stackFrame,
        frameProxy.indexFromBottom
    )
}
