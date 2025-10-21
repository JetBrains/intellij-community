// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.evaluate.compilation

import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.codegen.AsmUtil
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.impl.LocalVariableDescriptor
import org.jetbrains.kotlin.descriptors.impl.SyntheticFieldDescriptor
import org.jetbrains.kotlin.idea.base.utils.fqname.fqName
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.ExecutionContext
import org.jetbrains.kotlin.idea.debugger.base.util.safeLocation
import org.jetbrains.kotlin.idea.debugger.base.util.safeMethod
import org.jetbrains.kotlin.idea.debugger.core.stackFrame.getThisName
import org.jetbrains.kotlin.idea.debugger.evaluate.DebuggerFieldPropertyDescriptor
import org.jetbrains.kotlin.idea.debugger.evaluate.KotlinDebuggerEvaluationBundle
import org.jetbrains.kotlin.idea.debugger.evaluate.KotlinK1CodeFragmentFactory.Companion.FAKE_JAVA_CONTEXT_FUNCTION_NAME
import org.jetbrains.kotlin.idea.debugger.evaluate.compilation.CodeFragmentParameter.Dumb
import org.jetbrains.kotlin.idea.debugger.evaluate.compilation.CodeFragmentParameter.Kind
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isAncestor
import org.jetbrains.kotlin.psi.psiUtil.isDotSelector
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.checkers.COROUTINE_CONTEXT_FQ_NAME
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.VariableAsFunctionResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.scopes.receivers.*
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.expressions.createFunctionType

internal class K1CodeFragmentParameterInfo(
    val smartParameters: List<SmartCodeFragmentParameter>,
    override val crossingBounds: Set<Dumb>
) : CodeFragmentParameterInfo {
    override val parameters: List<Dumb> = object : AbstractList<Dumb>() {
        override val size: Int
            get() = smartParameters.size

        override fun get(index: Int): Dumb {
            return smartParameters[index].dumb
        }
    }
}

/*
    The purpose of this class is to figure out what parameters the received code fragment captures.
    It handles both directly mentioned names such as local variables or parameters and implicit values (dispatch/extension receivers).
 */
internal class CodeFragmentParameterAnalyzer(
    private val context: ExecutionContext,
    private val codeFragment: KtCodeFragment,
    private val bindingContext: BindingContext
) {
    private val parameters = LinkedHashMap<DeclarationDescriptor, SmartCodeFragmentParameter>()
    private val crossingBounds = mutableSetOf<Dumb>()

    private val onceUsedChecker = OnceUsedChecker(CodeFragmentParameterAnalyzer::class.java)

    private val containingPrimaryConstructor: ConstructorDescriptor? by lazy {
        context.frameProxy.safeLocation()?.safeMethod()?.takeIf { it.isConstructor } ?: return@lazy null
        val constructor = codeFragment.context?.getParentOfType<KtPrimaryConstructor>(false) ?: return@lazy null
        bindingContext[BindingContext.CONSTRUCTOR, constructor]
    }

    fun analyze(): K1CodeFragmentParameterInfo {
        onceUsedChecker.trigger()

        codeFragment.accept(object : KtTreeVisitor<Unit>() {
            override fun visitSimpleNameExpression(expression: KtSimpleNameExpression, data: Unit?): Void? {
                val resolvedCall = expression.getResolvedCall(bindingContext) ?: return null
                processResolvedCall(resolvedCall, expression)

                return null
            }

            private fun processResolvedCall(resolvedCall: ResolvedCall<*>, expression: KtSimpleNameExpression) {
                if (resolvedCall is VariableAsFunctionResolvedCall) {
                    processResolvedCall(resolvedCall.functionCall, expression)
                    processResolvedCall(resolvedCall.variableCall, expression)
                    return
                }

                val descriptor = resolvedCall.resultingDescriptor

                // Capture dispatch receiver for the extension callable
                run {
                    val containingClass = descriptor?.containingDeclaration as? ClassDescriptor
                    val extensionParameter = descriptor?.extensionReceiverParameter
                    if (descriptor != null && descriptor !is DebuggerFieldPropertyDescriptor
                        && extensionParameter != null && containingClass != null
                    ) {
                        if (containingClass.kind != ClassKind.OBJECT) {
                            val parameter = processDispatchReceiver(containingClass)
                            checkBounds(descriptor, expression, parameter)
                        }
                    }
                }

                if (runReadAction { expression.isDotSelector() }) {
                    val parameter = processCoroutineContextCall(resolvedCall.resultingDescriptor)
                    if (parameter != null) {
                        checkBounds(descriptor, expression, parameter)
                    }
                }

                if (isCodeFragmentDeclaration(descriptor)) {
                    // The reference is from the code fragment we analyze, no need to capture
                    return
                }

                var processed = false

                fun processImplicitReceiver(receiver: ReceiverValue?) {
                    if (receiver is ImplicitReceiver && !isPrimaryConstructorParameter(descriptor)) {
                        val parameter = processReceiver(receiver)
                        if (parameter != null) {
                            checkBounds(receiver.declarationDescriptor, expression, parameter)
                            processed = true
                        }
                    }
                }

                with(resolvedCall) {
                    processImplicitReceiver(dispatchReceiver)
                    processImplicitReceiver(extensionReceiver)
                    contextReceivers.forEach(::processImplicitReceiver)
                }

                if (!processed && descriptor is SyntheticFieldDescriptor) {
                    val parameter = processSyntheticFieldVariable(descriptor)
                    checkBounds(descriptor, expression, parameter)
                    processed = true
                }

                // If a reference has receivers, we can calculate its value using them, no need to capture
                if (!processed) {
                    val parameter = processDescriptor(descriptor, expression)
                    checkBounds(descriptor, expression, parameter)
                }
            }

            private fun processDescriptor(descriptor: DeclarationDescriptor, expression: KtSimpleNameExpression): SmartCodeFragmentParameter? {
                return processForeignProperty(descriptor)
                    ?: processCoroutineContextCall(descriptor)
                    ?: processSimpleNameExpression(descriptor, expression)
            }

            override fun visitThisExpression(expression: KtThisExpression, data: Unit?): Void? {
                val instanceReference = runReadAction { expression.instanceReference }
                val target = bindingContext[BindingContext.REFERENCE_TARGET, instanceReference]

                if (isCodeFragmentDeclaration(target)) {
                    // The reference is from the code fragment we analyze, no need to capture
                    return null
                }

                val parameter = when (target) {
                    is ClassDescriptor -> processDispatchReceiver(target)
                    is CallableDescriptor -> {
                        val type = bindingContext[BindingContext.EXPRESSION_TYPE_INFO, expression]?.type ?: return null
                        processContextReceiver(target, type)
                            ?: processExtensionReceiver(target, type, expression.getLabelName())
                    }
                    else -> null
                }

                checkBounds(target, expression, parameter)

                return null
            }

            override fun visitSuperExpression(expression: KtSuperExpression, data: Unit?): Void? {
                val type = bindingContext[BindingContext.THIS_TYPE_FOR_SUPER_EXPRESSION, expression] ?: return null
                val descriptor = type.constructor.declarationDescriptor as? ClassDescriptor ?: return null

                parameters.getOrPut(descriptor) {
                    val name = descriptor.name.asString()
                    SmartCodeFragmentParameter(Dumb(Kind.DISPATCH_RECEIVER, "", depthRelativeToCurrentFrame = 0, "super@$name"), type, descriptor)
                }

                return null
            }

            override fun visitCallExpression(expression: KtCallExpression, data: Unit?): Void? {
                val resolvedCall = expression.getResolvedCall(bindingContext)
                if (resolvedCall != null) {
                    val descriptor = resolvedCall.resultingDescriptor
                    if (descriptor is ConstructorDescriptor && KotlinBuiltIns.isNothing(descriptor.returnType)) {
                        throw EvaluateExceptionUtil.createEvaluateException(
                            KotlinDebuggerEvaluationBundle.message("error.nothing.initialization")
                        )
                    }
                }

                return super.visitCallExpression(expression, data)
            }
        }, Unit)

        return K1CodeFragmentParameterInfo(parameters.values.toList(), crossingBounds)
    }

    private fun processReceiver(receiver: ImplicitReceiver): SmartCodeFragmentParameter? {
        if (isCodeFragmentDeclaration(receiver.declarationDescriptor)) {
            return null
        }

        return when (receiver) {
            is ImplicitClassReceiver -> processDispatchReceiver(receiver.classDescriptor)
            is ExtensionReceiver -> processExtensionReceiver(receiver.declarationDescriptor, receiver.type, null)
            is ContextReceiver -> processContextReceiver(receiver.declarationDescriptor, receiver.type)
            is ContextClassReceiver -> processDispatchReceiver(receiver.classDescriptor)
            else -> null
        }
    }

    private fun processDispatchReceiver(descriptor: ClassDescriptor): SmartCodeFragmentParameter? {
        if (descriptor.kind == ClassKind.OBJECT) {
            return null
        }

        val type = descriptor.defaultType
        return parameters.getOrPut(descriptor) {
            val name = descriptor.name
            val debugLabel = if (name.isSpecial) "" else "@" + name.asString()
            SmartCodeFragmentParameter(Dumb(Kind.DISPATCH_RECEIVER, "", depthRelativeToCurrentFrame = 0, AsmUtil.THIS + debugLabel), type, descriptor)
        }
    }

    private fun processExtensionReceiver(descriptor: CallableDescriptor, receiverType: KotlinType, label: String?): SmartCodeFragmentParameter? {
        if (isFakeFunctionForJavaContext(descriptor)) {
            return processFakeJavaCodeReceiver(descriptor)
        }

        val actualLabel = label ?: getLabel(descriptor)
        val receiverParameter = descriptor.extensionReceiverParameter ?: return null

        return parameters.getOrPut(descriptor) {
            SmartCodeFragmentParameter(
                Dumb(Kind.EXTENSION_RECEIVER, actualLabel, depthRelativeToCurrentFrame = 0, AsmUtil.THIS + "@" + actualLabel),
                receiverType,
                receiverParameter
            )
        }
    }

    private fun getLabel(callableDescriptor: CallableDescriptor): String {
        val source = callableDescriptor.source.getPsi()

        if (source is KtFunctionLiteral) {
            getCallLabelForLambdaArgument(source, bindingContext)?.let { return it }
        }

        // In case of special name we will end up with "null" result.
        // This is expected behavior for K1 that is inlined with a way how `StackFrameProxyImpl` stores variables.
        // For K2 we will have a bit different name stored in `StackFrameProxyImpl`,
        // but still we will be able to find the correct one due to how `VariableFinder` works.
        return callableDescriptor.name.takeIf { !it.isSpecial }.toString()
    }

    private fun isFakeFunctionForJavaContext(descriptor: CallableDescriptor): Boolean {
        return descriptor is FunctionDescriptor
                && descriptor.name.asString() == FAKE_JAVA_CONTEXT_FUNCTION_NAME
                && codeFragment.getCopyableUserData(KtCodeFragment.FAKE_CONTEXT_FOR_JAVA_FILE) != null
    }


    private fun processContextReceiver(descriptor: CallableDescriptor, receiverType: KotlinType): SmartCodeFragmentParameter? {
        val receiverParameter = descriptor.contextReceiverParameters.find { it.type == receiverType } ?: return null

        return parameters.getOrPut(receiverParameter) {
            val name = receiverParameter.name.asString()
            val label = getThisName("${receiverType.fqName?.shortName()}")
            SmartCodeFragmentParameter(Dumb(Kind.CONTEXT_RECEIVER, name, depthRelativeToCurrentFrame = 0, label), receiverType, receiverParameter)
        }
    }

    private fun processFakeJavaCodeReceiver(descriptor: CallableDescriptor): SmartCodeFragmentParameter? {
        val receiverParameter = descriptor
            .takeIf { descriptor is FunctionDescriptor }
            ?.extensionReceiverParameter
            ?: return null

        val label = FAKE_JAVA_CONTEXT_FUNCTION_NAME
        val type = receiverParameter.type
        return parameters.getOrPut(descriptor) {
            SmartCodeFragmentParameter(Dumb(Kind.FAKE_JAVA_OUTER_CLASS, label, depthRelativeToCurrentFrame = 0, AsmUtil.THIS), type, receiverParameter)
        }
    }

    private fun processSyntheticFieldVariable(descriptor: SyntheticFieldDescriptor): SmartCodeFragmentParameter {
        val propertyDescriptor = descriptor.propertyDescriptor
        val fieldName = propertyDescriptor.name.asString()
        val type = propertyDescriptor.type
        return parameters.getOrPut(descriptor) {
            SmartCodeFragmentParameter(Dumb(Kind.FIELD_VAR, fieldName, depthRelativeToCurrentFrame = 0, "field"), type, descriptor)
        }
    }

    private fun processSimpleNameExpression(target: DeclarationDescriptor, expression: KtSimpleNameExpression): SmartCodeFragmentParameter? {
        if (target is ValueParameterDescriptor && target.isCrossinline) {
            throw EvaluateExceptionUtil.createEvaluateException(
                KotlinDebuggerEvaluationBundle.message("error.crossinline.lambda.evaluation")
            )
        }

        if (!isPrimaryConstructorParameter(target)) {
            return null
        }

        return when (target) {
            is SimpleFunctionDescriptor -> {
                val type = target.createFunctionType(target.builtIns, target.isSuspend) ?: return null
                parameters.getOrPut(target) {
                    SmartCodeFragmentParameter(Dumb(Kind.LOCAL_FUNCTION, target.name.asString(), depthRelativeToCurrentFrame = 0), type, target.original)
                }
            }
            is ValueDescriptor -> {
                val unwrappedExpression = KtPsiUtil.deparenthesize(expression)
                val isLValue = unwrappedExpression?.let { isAssignmentLValue(it) } ?: false

                parameters.getOrPut(target) {
                    val kind = if (target is LocalVariableDescriptor && target.isDelegated) Kind.DELEGATED else Kind.ORDINARY
                    SmartCodeFragmentParameter(Dumb(kind, target.name.asString(), depthRelativeToCurrentFrame = 0), target.type, target, isLValue)
                }
            }
            else -> null
        }
    }

    private fun isPrimaryConstructorParameter(target: DeclarationDescriptor): Boolean {
        val isLocalTarget = (target as? DeclarationDescriptorWithVisibility)?.visibility == DescriptorVisibilities.LOCAL

        val isPrimaryConstructorParameter = !isLocalTarget
                && target is PropertyDescriptor
                && isContainingPrimaryConstructorParameter(target)

        return isLocalTarget || isPrimaryConstructorParameter
    }

    private fun isAssignmentLValue(expression: PsiElement): Boolean {
        val assignmentExpression = (expression.parent as? KtBinaryExpression)?.takeIf { KtPsiUtil.isAssignment(it) } ?: return false
        return assignmentExpression.left == expression
    }

    private fun isContainingPrimaryConstructorParameter(target: PropertyDescriptor): Boolean {
        val primaryConstructor = containingPrimaryConstructor ?: return false
        for (parameter in primaryConstructor.valueParameters) {
            val property = bindingContext[BindingContext.VALUE_PARAMETER_AS_PROPERTY, parameter]
            if (target == property) {
                return true
            }
        }

        return false
    }

    private fun processCoroutineContextCall(target: DeclarationDescriptor): SmartCodeFragmentParameter? {
        if (target is PropertyDescriptor && target.fqNameSafe == COROUTINE_CONTEXT_FQ_NAME) {
            return parameters.getOrPut(target) {
                SmartCodeFragmentParameter(Dumb(Kind.COROUTINE_CONTEXT, "", depthRelativeToCurrentFrame = 0), target.type, target)
            }
        }

        return null
    }

    private fun processForeignProperty(target: DeclarationDescriptor): SmartCodeFragmentParameter? {
        val descriptor = target as? ForeignPropertyDescriptor ?: return null

        return parameters.getOrPut(target) {
            SmartCodeFragmentParameter(Dumb(Kind.FOREIGN_VALUE, descriptor.propertyName, depthRelativeToCurrentFrame = 0), descriptor.type, descriptor)
        }
    }

    fun checkBounds(descriptor: DeclarationDescriptor?, expression: KtExpression, parameter: SmartCodeFragmentParameter?) {
        if (parameter == null || descriptor !is DeclarationDescriptorWithSource) {
            return
        }

        val targetPsi = descriptor.source.getPsi()
        if (targetPsi != null && doesCrossInlineBounds(expression, targetPsi)) {
            crossingBounds += parameter.dumb
        }
    }

    private fun doesCrossInlineBounds(expression: PsiElement, declaration: PsiElement): Boolean {

        // Trying to find a common parent for declaration and expression
        fun findCommonParent(declaration: PsiElement): PsiElement? {
            val declarationParent = declaration.parent
            return when {
                declarationParent is KtDestructuringDeclaration -> declarationParent.getParentOfType<KtBlockExpression>(true)
                declarationParent is KtParameterList && declarationParent.parent is KtPrimaryConstructor -> declaration.getParentOfType<KtClass>(true)
                declarationParent is KtParameterList && declarationParent.parent is KtFunction -> declarationParent.parent
                else -> declarationParent
            }
        }

        val declarationCommonParent = findCommonParent(declaration) ?: return false
        var currentParent: PsiElement? = expression.parent?.takeIf { it.isInside(declarationCommonParent) } ?: return false

        while (currentParent != null && currentParent != declarationCommonParent) {
            if (currentParent is KtFunction) {
                val functionDescriptor = bindingContext[BindingContext.FUNCTION, currentParent]
                if (functionDescriptor != null && !functionDescriptor.isInline) {
                    return true
                }
            }

            currentParent = when (currentParent) {
                is KtCodeFragment -> currentParent.context
                else -> currentParent.parent
            }
        }

        return false
    }

    private fun isCodeFragmentDeclaration(descriptor: DeclarationDescriptor?): Boolean {
        if (descriptor is ValueParameterDescriptor && isCodeFragmentDeclaration(descriptor.containingDeclaration)) {
            return true
        }

        if (descriptor !is DeclarationDescriptorWithSource) {
            return false
        }

        return descriptor.source.getPsi()?.containingFile is KtCodeFragment
    }

    private tailrec fun PsiElement.isInside(parent: PsiElement): Boolean {
        if (parent.isAncestor(this)) {
            return true
        }

        val context = (this.containingFile as? KtCodeFragment)?.context ?: return false
        return context.isInside(parent)
    }
}

private class OnceUsedChecker(private val clazz: Class<*>) {
    private var used = false

    fun trigger() {
        if (used) {
            error(clazz.name + " may be only used once")
        }

        used = true
    }
}

fun getCallLabelForLambdaArgument(declaration: KtFunctionLiteral, bindingContext: BindingContext): String? {
    val lambdaExpression = declaration.parent as? KtLambdaExpression ?: return null
    val lambdaExpressionParent = lambdaExpression.parent

    if (lambdaExpressionParent is KtLabeledExpression) {
        lambdaExpressionParent.name?.let { return it }
    }

    val callExpression = when (val argument = lambdaExpression.parent) {
        is KtLambdaArgument -> {
            argument.parent as? KtCallExpression ?: return null
        }
        is KtValueArgument -> {
            val valueArgumentList = argument.parent as? KtValueArgumentList ?: return null
            valueArgumentList.parent as? KtCallExpression ?: return null
        }
        else -> return null
    }

    val call = callExpression.getResolvedCall(bindingContext) ?: return null
    return call.resultingDescriptor.name.asString()
}
