/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger.coroutine.proxy

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.sun.jdi.Location
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value
import org.jetbrains.kotlin.idea.debugger.coroutine.data.ContinuationVariableValueDescriptorImpl
import org.jetbrains.kotlin.idea.debugger.wrapEvaluateException

class SkipCoroutineStackFrameProxyImpl(frame: StackFrameProxyImpl) :
    StackFrameProxyImpl(frame.threadProxy(), frame.stackFrame, frame.indexFromBottom)

class CoroutineStackFrameProxyImpl(
    private val location: Location?,
    val spilledVariables: List<JavaValue>,
    frame: StackFrameProxyImpl
) : StackFrameProxyImpl(frame.threadProxy(), frame.stackFrame, frame.indexFromBottom) {
    val continuation = wrapEvaluateException { super.thisObject() }
    private val coroutineScope = extractCoroutineScope()

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
        val suspendContext = debugProcess.suspendManager.pausedContext
        val evaluationContext = EvaluationContextImpl(suspendContext, this)
        return CoroutineScopeExtractor.extractCoroutineScope(continuation, evaluationContext)
    }
}
