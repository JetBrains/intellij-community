// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger

import com.intellij.debugger.DebuggerContext
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl
import com.intellij.debugger.ui.tree.render.OnDemandRenderer
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiExpression
import com.intellij.xdebugger.frame.XValueNode
import com.intellij.xdebugger.frame.presentation.XRegularValuePresentation
import com.sun.jdi.Method
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value

class GetterDescriptor(
  val parentDescriptor: ValueDescriptorImpl,
  val getter: Method,
  project: Project
) : ValueDescriptorImpl(project) {
    private val name = getter.name().drop(3).decapitalize()

    init {
        OnDemandRenderer.ON_DEMAND_CALCULATED.set(this, false)
    }

    override fun getDescriptorEvaluation(context: DebuggerContext): PsiExpression = throw EvaluateException("Getter evaluation is not supported")

    override fun setOnDemandEvaluationPresentation(node: XValueNode) {
        node.setFullValueEvaluator(OnDemandRenderer.createFullValueEvaluator("... get()"))
        node.setPresentation(AllIcons.Nodes.Property, XRegularValuePresentation("", null, ""), false)
    }

    override fun getName() = name

    override fun calcValue(evaluationContext: EvaluationContextImpl?): Value? {
        val value = parentDescriptor.value as? ObjectReference ?: return null
        return evaluationContext?.debugProcess?.invokeMethod(evaluationContext, value, getter, emptyList())
    }
}
