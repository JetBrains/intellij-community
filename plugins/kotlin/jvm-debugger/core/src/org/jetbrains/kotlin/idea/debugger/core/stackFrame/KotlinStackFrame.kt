// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.core.stackFrame

import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.jdi.LocalVariableProxyImpl
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.ui.impl.watch.MethodsTracker
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl
import com.intellij.xdebugger.frame.XValueChildrenList
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value
import org.jetbrains.kotlin.codegen.AsmUtil
import org.jetbrains.kotlin.codegen.AsmUtil.THIS
import org.jetbrains.kotlin.codegen.DESTRUCTURED_LAMBDA_ARGUMENT_VARIABLE_PREFIX
import org.jetbrains.kotlin.codegen.coroutines.CONTINUATION_VARIABLE_NAME
import org.jetbrains.kotlin.codegen.coroutines.SUSPEND_FUNCTION_COMPLETION_PARAMETER_NAME
import org.jetbrains.kotlin.codegen.inline.INLINE_FUN_VAR_SUFFIX
import org.jetbrains.kotlin.codegen.inline.INLINE_SCOPE_NUMBER_SEPARATOR
import org.jetbrains.kotlin.codegen.inline.dropInlineScopeInfo
import org.jetbrains.kotlin.codegen.inline.isFakeLocalVariableForInline
import org.jetbrains.kotlin.idea.debugger.base.util.*
import org.jetbrains.kotlin.idea.debugger.core.ToggleKotlinVariablesState
import org.jetbrains.kotlin.name.NameUtils.CONTEXT_RECEIVER_PREFIX

@Suppress("EqualsOrHashCode")
open class KotlinStackFrame(
    stackFrameDescriptorImpl: StackFrameDescriptorImpl,
    visibleVariables: List<LocalVariableProxyImpl>
) : JavaStackFrame(stackFrameDescriptorImpl, true) {
    private val kotlinVariableViewService = ToggleKotlinVariablesState.getService()

    constructor(frame: StackFrameProxyImpl, visibleVariables: List<LocalVariableProxyImpl>) :
            this(StackFrameDescriptorImpl(frame, MethodsTracker()), visibleVariables)

    override fun buildLocalVariables(
        evaluationContext: EvaluationContextImpl,
        children: XValueChildrenList,
        localVariables: List<LocalVariableProxyImpl>
    ) {
        if (!kotlinVariableViewService.kotlinVariableView) {
            return super.buildLocalVariables(evaluationContext, children, localVariables)
        }
        buildVariablesInKotlinView(evaluationContext, children, localVariables)
    }

    override fun superBuildVariables(evaluationContext: EvaluationContextImpl, children: XValueChildrenList) {
        if (!kotlinVariableViewService.kotlinVariableView) {
            return super.superBuildVariables(evaluationContext, children)
        }
        buildVariablesInKotlinView(evaluationContext, children, visibleVariables)
    }

    private fun buildVariablesInKotlinView(
        evaluationContext: EvaluationContextImpl,
        children: XValueChildrenList,
        variables: List<LocalVariableProxyImpl>
    ) {
        val (thisVariables, otherVariables) = variables
            .partition { it.nameWithoutScopeNumber() == THIS || it is ThisLocalVariable }

        val existingVariables = ExistingVariables(thisVariables, otherVariables)

        if (!removeSyntheticThisObject(evaluationContext, children, existingVariables) && thisVariables.isNotEmpty()) {
            remapThisObjectForOuterThis(evaluationContext, children, existingVariables)
        }
        children.add(evaluationContext, thisVariables, otherVariables)
        for (contributor in KotlinStackFrameValueContributor.EP.extensions) {
            for (value in contributor.contributeValues(this, evaluationContext, variables)) {
                children.add(value)
            }
        }
    }

    private fun XValueChildrenList.add(
        evaluationContext: EvaluationContextImpl,
        thisVariables: List<LocalVariableProxyImpl>,
        otherVariables: List<LocalVariableProxyImpl>
    ) {
        val nodeManager = evaluationContext.debugProcess.xdebugProcess?.nodeManager ?: return

        fun addItem(variable: LocalVariableProxyImpl) {
            val variableDescriptor = nodeManager.getLocalVariableDescriptor(null, variable)
            add(JavaValue.create(null, variableDescriptor, evaluationContext, nodeManager, false))
        }

        thisVariables.forEach(::addItem)
        otherVariables.forEach {
            val nameWithoutScope = it.nameWithoutScopeNumber()
            if (nameWithoutScope.startsWith(AsmUtil.CAPTURED_PREFIX)) {
                val valueData = CapturedAsLocalVariableValueData(nameWithoutScope.drop(1), it)
                val variableDescriptor = nodeManager.getDescriptor(null, valueData)
                add(JavaValue.create(null, variableDescriptor, evaluationContext, nodeManager, false))
            } else if (it.name().contains(INLINE_SCOPE_NUMBER_SEPARATOR)) {
                addItem(it.clone(nameWithoutScope, null))
            } else {
                addItem(it)
            }
        }
    }

    private fun removeSyntheticThisObject(
        evaluationContext: EvaluationContextImpl,
        children: XValueChildrenList,
        existingVariables: ExistingVariables
    ): Boolean {
        val thisObject = evaluationContext.frameProxy?.thisObject() ?: return false

        val thisObjectType = thisObject.type()
        if (thisObjectType.isSubtype(Function::class.java.name) && '$' in thisObjectType.signature()) {
            val existingThis = ExistingInstanceThisRemapper.find(children)
            if (existingThis != null) {
                existingThis.remove()
                val containerJavaValue = existingThis.value as? JavaValue
                val containerValue = containerJavaValue?.descriptor?.calcValue(evaluationContext) as? ObjectReference
                if (containerValue != null) {
                    attachCapturedValues(evaluationContext, children, containerValue, existingVariables)
                }
            }
            return true
        }

        if (thisObject.type().isSubtype(CONTINUATION_TYPE)) {
            ExistingInstanceThisRemapper.find(children)?.remove()
            val dispatchReceiver = (evaluationContext.frameProxy as? KotlinStackFrameProxyImpl)?.dispatchReceiver() ?: return true
            val dispatchReceiverType = dispatchReceiver.type()
            if (dispatchReceiverType.isSubtype(Function::class.java.name) && '$' in dispatchReceiverType.signature()) {
                attachCapturedValues(evaluationContext, children, dispatchReceiver, existingVariables)
            }
            return true
        }

        return removeCallSiteThisInInlineFunction(evaluationContext, children)
    }

    private fun removeCallSiteThisInInlineFunction(evaluationContext: EvaluationContextImpl, children: XValueChildrenList): Boolean {
        val variables = evaluationContext.frameProxy?.safeVisibleVariables() ?: return false
        val declarationSiteThis = variables.firstOrNull { v ->
            val name = v.name()
            (name.endsWith(INLINE_FUN_VAR_SUFFIX) && dropInlineSuffix(name) == AsmUtil.INLINE_DECLARATION_SITE_THIS) ||
                    (name.contains(INLINE_SCOPE_NUMBER_SEPARATOR) && name.startsWith(AsmUtil.INLINE_DECLARATION_SITE_THIS))
        } ?: return false

        if (declarationSiteThis.name().contains(INLINE_SCOPE_NUMBER_SEPARATOR) || getInlineDepth(variables) > 0) {
            ExistingInstanceThisRemapper.find(children)?.remove()
            return true
        }

        return false
    }

    private fun attachCapturedValues(
        evaluationContext: EvaluationContextImpl,
        children: XValueChildrenList,
        containerValue: ObjectReference,
        existingVariables: ExistingVariables
    ) {
        val nodeManager = evaluationContext.debugProcess.xdebugProcess?.nodeManager ?: return

        attachCapturedValues(containerValue, existingVariables) { valueData ->
            val valueDescriptor = nodeManager.getDescriptor(this.descriptor, valueData)
            children.add(JavaValue.create(null, valueDescriptor, evaluationContext, nodeManager, false))
        }
    }

    private fun remapThisObjectForOuterThis(
        evaluationContext: EvaluationContextImpl,
        children: XValueChildrenList,
        existingVariables: ExistingVariables
    ) {
        val thisObject = evaluationContext.frameProxy?.thisObject() ?: return
        val variable = ExistingInstanceThisRemapper.find(children) ?: return

        val thisLabel = generateThisLabel(thisObject.referenceType())
        val thisName = thisLabel?.let(::getThisName)

        if (thisName == null || !existingVariables.add(ExistingVariable.LabeledThis(thisLabel))) {
            variable.remove()
            return
        }

        // add additional checks?
        variable.remapName(getThisName(thisLabel))
    }

    private val _visibleVariables: List<LocalVariableProxyImpl> = visibleVariables.remapInKotlinView()

    final override fun getVisibleVariables(): List<LocalVariableProxyImpl> {
        if (!kotlinVariableViewService.kotlinVariableView) {
            val allVisibleVariables = stackFrameProxy.safeVisibleVariables()
            return allVisibleVariables.map { variable ->
                if (isFakeLocalVariableForInline(variable.name())) variable.wrapSyntheticInlineVariable() else variable
            }
        }

        return _visibleVariables
    }

    private fun List<LocalVariableProxyImpl>.remapInKotlinView(): List<LocalVariableProxyImpl> {
        val (thisVariables, otherVariables) = filter { variable ->
                val name = variable.nameWithoutScopeNumber()
                !isFakeLocalVariableForInline(name) &&
                    !name.startsWith(DESTRUCTURED_LAMBDA_ARGUMENT_VARIABLE_PREFIX) &&
                    !name.startsWith(AsmUtil.LOCAL_FUNCTION_VARIABLE_PREFIX) &&
                    name != CONTINUATION_VARIABLE_NAME &&
                    name != SUSPEND_FUNCTION_COMPLETION_PARAMETER_NAME
            }.partition { variable ->
                val name = variable.nameWithoutScopeNumber()
                name == THIS ||
                    name == AsmUtil.THIS_IN_DEFAULT_IMPLS ||
                    name.startsWith(AsmUtil.LABELED_THIS_PARAMETER) ||
                    name == AsmUtil.INLINE_DECLARATION_SITE_THIS
            }

        // The variables are already sorted, so the mainThis is the last one in the list.
        val mainThis = thisVariables.lastOrNull()
        val otherThis = thisVariables.dropLast(1)

        val remappedMainThis = mainThis?.remapVariableIfNeeded(THIS)
        val remappedOtherThis = otherThis.map { it.remapVariableIfNeeded() }
        val remappedOther = otherVariables.map { it.remapVariableIfNeeded() }
        return (remappedOtherThis + listOfNotNull(remappedMainThis) + remappedOther)
    }

    private fun LocalVariableProxyImpl.remapVariableIfNeeded(customName: String? = null): LocalVariableProxyImpl {
        val name = dropInlineSuffix(nameWithoutScopeNumber())

        return when {
            name.startsWith(AsmUtil.LABELED_THIS_PARAMETER) -> {
                val label = name.drop(AsmUtil.LABELED_THIS_PARAMETER.length)
                clone(customName ?: getThisName(label), label)
            }
            name.startsWith(AsmUtil.CAPTURED_LABELED_THIS_FIELD) -> {
                val label = name.drop(AsmUtil.CAPTURED_LABELED_THIS_FIELD.length)
                clone(customName ?: getThisName(label), label)
            }
            name == AsmUtil.THIS_IN_DEFAULT_IMPLS -> clone(customName ?: ("$THIS (outer)"), null)
            name == AsmUtil.RECEIVER_PARAMETER_NAME -> clone(customName ?: ("$THIS (receiver)"), null)
            name == AsmUtil.INLINE_DECLARATION_SITE_THIS -> {
                val label = generateThisLabel(frame.getValue(this)?.type())
                if (label != null) {
                    clone(customName ?: getThisName(label), label)
                } else {
                    this
                }
            }
            name.startsWith(CONTEXT_RECEIVER_PREFIX) || name.startsWith(AsmUtil.CAPTURED_PREFIX + CONTEXT_RECEIVER_PREFIX) -> {
                val label = generateThisLabel(type)
                if (label != null) {
                    clone(getThisName(label), null)
                } else {
                    this
                }
            }
            name != name() -> {
                object : LocalVariableProxyImpl(frame, variable) {
                    override fun name() = name
                }
            }
            else -> this
        }
    }

    private fun LocalVariableProxyImpl.clone(name: String, label: String?): LocalVariableProxyImpl {
        return object : LocalVariableProxyImpl(frame, variable), ThisLocalVariable {
            override fun name() = name
            override val label = label
        }
    }

    override fun equals(other: Any?): Boolean {
        val eq = super.equals(other)
        return other is KotlinStackFrame && eq
    }
}

interface ThisLocalVariable {
    val label: String?
}

private fun LocalVariableProxyImpl.wrapSyntheticInlineVariable(): LocalVariableProxyImpl {
    val proxyWrapper = object : StackFrameProxyImpl(frame.threadProxy(), frame.stackFrame, frame.indexFromBottom) {
        override fun getValue(localVariable: LocalVariableProxyImpl): Value {
            return frame.virtualMachine.mirrorOfVoid()
        }
    }
    return LocalVariableProxyImpl(proxyWrapper, variable)
}

private fun LocalVariableProxyImpl.nameWithoutScopeNumber(): String =
    name().dropInlineScopeInfo()
