// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.core.stackFrame

import com.intellij.debugger.DebuggerContext
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.impl.descriptors.data.DescriptorData
import com.intellij.debugger.impl.descriptors.data.DisplayKey
import com.intellij.debugger.impl.descriptors.data.SimpleDisplayKey
import com.intellij.debugger.ui.impl.watch.*
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiExpression
import com.intellij.util.IncorrectOperationException
import com.sun.jdi.*

class LabeledThisData(val label: String, val name: String, val value: Value?) : DescriptorData<ValueDescriptorImpl>() {
    override fun createDescriptorImpl(project: Project): ValueDescriptorImpl {
        return object : ValueDescriptorImpl(project, value) {
            override fun getName() = this@LabeledThisData.name
            override fun calcValue(evaluationContext: EvaluationContextImpl?) = value
            override fun canSetValue() = false

            override fun getDescriptorEvaluation(context: DebuggerContext?): PsiExpression {
                // TODO change to labeled this
                val elementFactory = JavaPsiFacade.getElementFactory(myProject)
                try {
                    return elementFactory.createExpressionFromText("this", null)
                } catch (e: IncorrectOperationException) {
                    throw EvaluateException(e.message, e)
                }
            }
        }
    }

    override fun getDisplayKey(): DisplayKey<ValueDescriptorImpl> = SimpleDisplayKey(this)
    override fun equals(other: Any?) = other is LabeledThisData && other.name == name
    override fun hashCode() = name.hashCode()
}
