// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.core.stackFrame

import com.intellij.debugger.DebuggerContext
import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.impl.descriptors.data.DescriptorData
import com.intellij.debugger.impl.descriptors.data.DisplayKey
import com.intellij.debugger.impl.descriptors.data.SimpleDisplayKey
import com.intellij.debugger.jdi.LocalVariableProxyImpl
import com.intellij.debugger.ui.impl.watch.FieldDescriptorImpl
import com.intellij.debugger.ui.impl.watch.LocalVariableDescriptorImpl
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiExpression
import com.intellij.xdebugger.frame.XValueModifier
import com.sun.jdi.Field
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value

data class CapturedAsFieldValueData(
    val valueName: String,
    val obj: ObjectReference,
    val field: Field
) : DescriptorData<ValueDescriptorImpl>() {
    override fun createDescriptorImpl(project: Project): ValueDescriptorImpl {
        val fieldDescriptor = FieldDescriptorImpl(project, obj, field)
        return DelegateDescriptor(valueName, fieldDescriptor)
    }

    override fun getDisplayKey(): DisplayKey<ValueDescriptorImpl> = SimpleDisplayKey(field)
}

data class CapturedAsLocalVariableValueData(
    val valueName: String,
    val localVariable: LocalVariableProxyImpl
) : DescriptorData<ValueDescriptorImpl>() {
    override fun createDescriptorImpl(project: Project): ValueDescriptorImpl {
        val descriptor = LocalVariableDescriptorImpl(project, localVariable)
        return DelegateDescriptor(valueName, descriptor)
    }

    override fun getDisplayKey(): DisplayKey<ValueDescriptorImpl> = SimpleDisplayKey(localVariable)
}

class DelegateDescriptor(
    private val valueName: String,
    val delegate: ValueDescriptorImpl
) : ValueDescriptorImpl(delegate.project) {
    override fun getName() = valueName

    override fun calcValue(evaluationContext: EvaluationContextImpl?): Value? = delegate.calcValue(evaluationContext)
    override fun getDescriptorEvaluation(context: DebuggerContext?): PsiExpression = delegate.getDescriptorEvaluation(context)
    override fun getModifier(value: JavaValue?): XValueModifier = delegate.getModifier(value)
}
