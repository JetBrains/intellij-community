// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.coroutine.data

import com.intellij.debugger.DebuggerContext
import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.DefaultExecutionContext

class ContinuationVariableValueDescriptorImpl(
  defaultExecutionContext: DefaultExecutionContext,
  val continuation: ObjectReference,
  val fieldName: String,
  private val variableName: String
) : ValueDescriptorImpl(defaultExecutionContext.project) {

    init {
        setContext(defaultExecutionContext.evaluationContext)
    }

    override fun calcValueName() = variableName

    override fun calcValue(evaluationContext: EvaluationContextImpl?): Value? {
        val referenceType = continuation.referenceType() ?: return null
        val field = DebuggerUtils.findField(referenceType, fieldName) ?: return null
        return continuation.getValue(field)
    }

    fun updateValue(value: Value?) {
        val referenceType = continuation.referenceType() ?: return
        val field = DebuggerUtils.findField(referenceType, fieldName) ?: return
        continuation.setValue(field, value)
    }

    /**
     * @TODO implement
     */
    override fun getDescriptorEvaluation(context: DebuggerContext?) =
        throw EvaluateException("Spilled variable evaluation is not supported")
}
