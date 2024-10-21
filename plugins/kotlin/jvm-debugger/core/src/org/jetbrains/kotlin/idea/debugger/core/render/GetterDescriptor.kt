// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.core.render

import com.intellij.debugger.DebuggerContext
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl
import com.intellij.debugger.ui.tree.DescriptorWithParentObject
import com.intellij.debugger.ui.tree.render.NodeRenderer
import com.intellij.debugger.ui.tree.render.OnDemandRenderer
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiExpression
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import com.intellij.xdebugger.frame.presentation.XRegularValuePresentation
import com.sun.jdi.Method
import com.sun.jdi.ObjectReference
import com.sun.jdi.Type
import org.jetbrains.kotlin.idea.debugger.base.util.safeReturnType
import org.jetbrains.kotlin.idea.debugger.core.KotlinDebuggerCoreBundle
import java.util.concurrent.CompletableFuture

class GetterDescriptor(
    private val parentObject: ObjectReference,
    val getter: Method,
    project: Project
) : ValueDescriptorImpl(project), DescriptorWithParentObject {
    companion object {
        val GETTER_PREFIXES = arrayOf("get", "is")
    }

    private val name = getter.name().removeGetterPrefix().decapitalize()

    init {
        OnDemandRenderer.ON_DEMAND_CALCULATED.set(this, false)
        val returnTypeName = type?.name()
        setOnDemandPresentationProvider { node ->
            node.setFullValueEvaluator(OnDemandRenderer.createFullValueEvaluator(KotlinDebuggerCoreBundle.message("message.variables.property.get")))
            node.setPresentation(IconManager.getInstance().getPlatformIcon(PlatformIcons.Property), XRegularValuePresentation("", returnTypeName, " "), false)
        }
    }

    private fun String.removeGetterPrefix(): String {
        if (startsWith("get")) {
            return drop(3)
        }
        // For properties starting with 'is' leave the name unmodified
        return this
    }

    override fun getObject() = parentObject

    override fun getDescriptorEvaluation(context: DebuggerContext): PsiExpression =
        throw EvaluateException("Getter evaluation is not supported")

    override fun getName() = name

    override fun getType(): Type? = getter.safeReturnType()

    override fun getRenderer(debugProcess: DebugProcessImpl?): CompletableFuture<NodeRenderer> =
        getRenderer(type, debugProcess)

    override fun calcValue(evaluationContext: EvaluationContextImpl?) =
        evaluationContext?.debugProcess?.invokeMethod(evaluationContext, parentObject, getter, emptyList())
}
