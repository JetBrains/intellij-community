// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import com.intellij.debugger.actions.MethodSmartStepTarget
import com.intellij.debugger.actions.SmartStepTarget
import com.intellij.psi.PsiMethod
import com.intellij.util.Range
import com.intellij.util.containers.OrderedSet
import org.jetbrains.kotlin.builtins.functions.FunctionInvokeDescriptor
import org.jetbrains.kotlin.builtins.isBuiltinFunctionalType
import org.jetbrains.kotlin.builtins.isFunctionType
import org.jetbrains.kotlin.codegen.intrinsics.IntrinsicMethods
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.debugger.breakpoints.isInlineOnly
import org.jetbrains.kotlin.idea.project.platform
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.load.java.isFromJava
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.platform.jvm.JdkPlatform
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.annotations.argumentValue
import org.jetbrains.kotlin.resolve.calls.callUtil.getParentCall
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.isInlineClassType
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.SimpleType
import org.jetbrains.kotlin.types.checker.isSingleClassifierType
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

// TODO support class initializers, local functions, delegated properties with specified type, setter for properties
class SmartStepTargetVisitor(
    private val element: KtElement,
    private val lines: Range<Int>,
    private val consumer: OrderedSet<SmartStepTarget>
) : KtTreeVisitorVoid() {
    private fun append(target: SmartStepTarget) {
        consumer += target
    }

    private val intrinsicMethods = run {
        val jvmTarget = element.platform.firstIsInstanceOrNull<JdkPlatform>()?.targetVersion ?: JvmTarget.DEFAULT
        IntrinsicMethods(jvmTarget)
    }

    override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression) {
        recordFunction(lambdaExpression.functionLiteral)
    }

    override fun visitNamedFunction(function: KtNamedFunction) {
        if (!recordFunction(function)) {
            super.visitNamedFunction(function)
        }
    }

    override fun visitCallableReferenceExpression(expression: KtCallableReferenceExpression) {
        recordCallableReference(expression)
        super.visitCallableReferenceExpression(expression)
    }

    private fun recordCallableReference(expression: KtCallableReferenceExpression) {
        val bindingContext = expression.analyze(BodyResolveMode.PARTIAL)
        val resolvedCall = expression.callableReference.getResolvedCall(bindingContext) ?: return
        when (val descriptor = resolvedCall.resultingDescriptor) {
            is FunctionDescriptor -> recordFunctionReference(expression, descriptor)
            is PropertyDescriptor -> recordGetter(expression, descriptor, bindingContext)
        }
    }

    private fun recordFunctionReference(expression: KtCallableReferenceExpression, descriptor: FunctionDescriptor) {
        val declaration = DescriptorToSourceUtilsIde.getAnyDeclaration(element.project, descriptor)
        if (descriptor.isFromJava && declaration is PsiMethod) {
            append(MethodSmartStepTarget(declaration, null, expression, true, lines))
        } else if (declaration is KtNamedFunction) {
            val label = KotlinMethodSmartStepTarget.calcLabel(descriptor)
            append(
                KotlinMethodReferenceSmartStepTarget(
                    lines,
                    expression,
                    label,
                    declaration,
                    CallableMemberInfo(descriptor)
                )
            )
        }
    }

    private fun recordGetter(expression: KtExpression, descriptor: PropertyDescriptor, bindingContext: BindingContext) {
        val getterDescriptor = descriptor.getter
        if (getterDescriptor == null || getterDescriptor.isDefault) return

        val ktDeclaration = DescriptorToSourceUtilsIde.getAnyDeclaration(element.project, getterDescriptor) as? KtDeclaration ?: return

        val delegatedResolvedCall = bindingContext[BindingContext.DELEGATED_PROPERTY_RESOLVED_CALL, getterDescriptor]
        if (delegatedResolvedCall != null) {
            val delegatedPropertyGetterDescriptor = delegatedResolvedCall.resultingDescriptor
            val delegateDeclaration = DescriptorToSourceUtilsIde.getAnyDeclaration(
                element.project, delegatedPropertyGetterDescriptor
            ) as? KtDeclarationWithBody ?: return
            val label = "${descriptor.name}." + KotlinMethodSmartStepTarget.calcLabel(delegatedPropertyGetterDescriptor)
            appendPropertyFilter(delegatedPropertyGetterDescriptor, delegateDeclaration, label, expression, lines)
        } else {
            if (ktDeclaration is KtPropertyAccessor && ktDeclaration.hasBody()) {
                val label = KotlinMethodSmartStepTarget.calcLabel(getterDescriptor)
                appendPropertyFilter(getterDescriptor, ktDeclaration, label, expression, lines)
            }
        }
    }

    private fun appendPropertyFilter(
        descriptor: CallableMemberDescriptor,
        declaration: KtDeclarationWithBody,
        label: String,
        expression: KtExpression,
        lines: Range<Int>
    ) {
        val methodInfo = CallableMemberInfo(descriptor)
        when (expression) {
            is KtCallableReferenceExpression ->
                append(
                    KotlinMethodReferenceSmartStepTarget(
                        lines,
                        expression,
                        label,
                        declaration,
                        methodInfo
                    )
                )
            else -> {
                val ordinal = countExistingMethodCalls(declaration)
                append(
                    KotlinMethodSmartStepTarget(
                        lines,
                        expression,
                        label,
                        declaration,
                        ordinal,
                        methodInfo
                    )
                )
            }
        }
    }

    private fun recordFunction(function: KtFunction): Boolean {
        val (parameter, resultingDescriptor) = function.getParameterAndResolvedCallDescriptor() ?: return false
        val target = createSmartStepTarget(function, parameter, resultingDescriptor)
        if (target != null) {
            append(target)
            return true
        }
        return false
    }

    private fun createSmartStepTarget(
        function: KtFunction,
        parameter: ValueParameterDescriptor,
        resultingDescriptor: CallableMemberDescriptor
    ): KotlinLambdaSmartStepTarget? {
        val declaration = DescriptorToSourceUtilsIde.getAnyDeclaration(element.project, resultingDescriptor) as? KtDeclaration ?: return null
        val callerMethodOrdinal = countExistingMethodCalls(declaration)
        if (parameter.isSamLambdaParameterDescriptor()) {
            val methodDescriptor = parameter.type.getFirstAbstractMethodDescriptor() ?: return null
            return KotlinLambdaSmartStepTarget(
                function,
                declaration,
                lines,
                KotlinLambdaInfo(
                    resultingDescriptor,
                    parameter,
                    callerMethodOrdinal,
                    methodDescriptor.containsInlineClassInValueArguments(),
                    true,
                    methodDescriptor.getMethodName()
                )
            )
        }
        return KotlinLambdaSmartStepTarget(
            function,
            declaration,
            lines,
            KotlinLambdaInfo(
                resultingDescriptor,
                parameter,
                callerMethodOrdinal,
                parameter.type.arguments.any { it.type.isInlineClassType() }
            )
        )
    }

    private fun countExistingMethodCalls(declaration: KtDeclaration): Int {
        return consumer
            .filterIsInstance<KotlinMethodSmartStepTarget>()
            .count {
                val targetDeclaration = it.getDeclaration()
                targetDeclaration != null && targetDeclaration === declaration
            }
    }

    override fun visitObjectLiteralExpression(expression: KtObjectLiteralExpression) {
        // Skip calls in object declarations
    }

    override fun visitIfExpression(expression: KtIfExpression) {
        expression.condition?.accept(this)
    }

    override fun visitWhileExpression(expression: KtWhileExpression) {
        expression.condition?.accept(this)
    }

    override fun visitDoWhileExpression(expression: KtDoWhileExpression) {
        expression.condition?.accept(this)
    }

    override fun visitForExpression(expression: KtForExpression) {
        expression.loopRange?.accept(this)
    }

    override fun visitWhenExpression(expression: KtWhenExpression) {
        expression.subjectExpression?.accept(this)
    }

    override fun visitArrayAccessExpression(expression: KtArrayAccessExpression) {
        super.visitArrayAccessExpression(expression)
        recordFunctionCall(expression)
    }

    override fun visitUnaryExpression(expression: KtUnaryExpression) {
        super.visitUnaryExpression(expression)
        recordFunctionCall(expression.operationReference)
    }

    override fun visitBinaryExpression(expression: KtBinaryExpression) {
        super.visitBinaryExpression(expression)
        recordFunctionCall(expression.operationReference)
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        val calleeExpression = expression.calleeExpression
        if (calleeExpression != null) {
            recordFunctionCall(calleeExpression)
        }
        super.visitCallExpression(expression)
    }

    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
        val bindingContext = expression.analyze(BodyResolveMode.PARTIAL)
        val resolvedCall = expression.getResolvedCall(bindingContext) ?: return
        val propertyDescriptor = resolvedCall.resultingDescriptor as? PropertyDescriptor ?: return
        recordGetter(expression, propertyDescriptor, bindingContext)
        super.visitSimpleNameExpression(expression)
    }

    private fun recordFunctionCall(expression: KtExpression) {
        val resolvedCall = expression.resolveToCall() ?: return
        val descriptor = resolvedCall.resultingDescriptor
        if (descriptor !is FunctionDescriptor || isIntrinsic(descriptor)) return

        val declaration = DescriptorToSourceUtilsIde.getAnyDeclaration(element.project, descriptor)
        if (descriptor.isFromJava) {
            if (declaration is PsiMethod) {
                append(MethodSmartStepTarget(declaration, null, expression, false, lines))
            }
        } else {
            if (declaration == null && !isInvokeInBuiltinFunction(descriptor)) {
                return
            }

            if (declaration !is KtDeclaration?) return

            if (descriptor is ConstructorDescriptor && descriptor.isPrimary) {
                if (declaration is KtClass && declaration.getAnonymousInitializers().isEmpty()) {
                    // There is no constructor or init block, so do not show it in smart step into
                    return
                }
            }

            // We can't step into @InlineOnly callables as there is no LVT, so skip them
            if (declaration is KtCallableDeclaration && declaration.isInlineOnly()) {
                return
            }

            val callLabel = KotlinMethodSmartStepTarget.calcLabel(descriptor)
            val label = when (descriptor) {
                is FunctionInvokeDescriptor -> {
                    when (expression) {
                        is KtSimpleNameExpression -> "${runReadAction { expression.text }}.$callLabel"
                        else -> callLabel
                    }
                }
                else -> callLabel
            }

            val ordinal = if (declaration == null) 0 else countExistingMethodCalls(declaration)
            append(
                KotlinMethodSmartStepTarget(
                    lines,
                    expression,
                    label,
                    declaration,
                    ordinal,
                    CallableMemberInfo(descriptor)
                )
            )
        }
    }

    private fun isIntrinsic(descriptor: CallableMemberDescriptor): Boolean {
        return intrinsicMethods.getIntrinsic(descriptor) != null
    }
}

private val JVM_NAME_FQ_NAME = FqName("kotlin.jvm.JvmName")

private fun PropertyAccessorDescriptor.getJvmMethodName(): String {
    val jvmNameAnnotation = annotations.findAnnotation(JVM_NAME_FQ_NAME)
    val jvmName = jvmNameAnnotation?.argumentValue(JvmName::name.name)?.value as? String
    if (jvmName != null) {
        return jvmName
    }
    return JvmAbi.getterName(correspondingProperty.name.asString())
}

fun DeclarationDescriptor.getMethodName() =
    when (this) {
        is ClassDescriptor, is ConstructorDescriptor -> "<init>"
        is PropertyAccessorDescriptor -> getJvmMethodName()
        else -> name.asString()
    }

fun KtFunction.isSamLambda(): Boolean {
    val (parameter, _) = getParameterAndResolvedCallDescriptor() ?: return false
    return parameter.isSamLambdaParameterDescriptor()
}

private fun ValueParameterDescriptor.isSamLambdaParameterDescriptor(): Boolean {
    val type = type
    return !type.isFunctionType && type is SimpleType && type.isSingleClassifierType
}

private fun KtFunction.getParameterAndResolvedCallDescriptor(): Pair<ValueParameterDescriptor, CallableMemberDescriptor>? {
    val context = analyze()
    val resolvedCall = getParentCall(context).getResolvedCall(context) ?: return null
    val descriptor = resolvedCall.resultingDescriptor as? CallableMemberDescriptor ?: return null
    val arguments = resolvedCall.valueArguments

    for ((param, argument) in arguments) {
        if (argument.arguments.any { getArgumentExpression(it) == this }) {
            return Pair(param, descriptor)
        }
    }
    return null
}

private fun getArgumentExpression(it: ValueArgument): KtExpression? {
    return (it.getArgumentExpression() as? KtLambdaExpression)?.functionLiteral ?: it.getArgumentExpression()
}

private fun KotlinType.getFirstAbstractMethodDescriptor(): CallableMemberDescriptor? =
    memberScope
        .getDescriptorsFiltered(DescriptorKindFilter.FUNCTIONS)
        .asSequence()
        .filterIsInstance<CallableMemberDescriptor>()
        .firstOrNull {
            it.modality == Modality.ABSTRACT
        }

private fun isInvokeInBuiltinFunction(descriptor: DeclarationDescriptor): Boolean {
    if (descriptor !is FunctionInvokeDescriptor) return false
    val classDescriptor = descriptor.containingDeclaration as? ClassDescriptor ?: return false
    return classDescriptor.defaultType.isBuiltinFunctionalType
}
