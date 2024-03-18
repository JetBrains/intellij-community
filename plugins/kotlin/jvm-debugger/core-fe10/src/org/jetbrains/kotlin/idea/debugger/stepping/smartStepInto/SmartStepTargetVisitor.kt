// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import com.intellij.debugger.actions.MethodSmartStepTarget
import com.intellij.debugger.actions.SmartStepTarget
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiMethod
import com.intellij.util.Range
import com.intellij.util.containers.OrderedSet
import org.jetbrains.kotlin.builtins.functions.FunctionInvokeDescriptor
import org.jetbrains.kotlin.builtins.isBuiltinFunctionalType
import org.jetbrains.kotlin.builtins.isFunctionType
import org.jetbrains.kotlin.builtins.isSuspendFunctionType
import org.jetbrains.kotlin.codegen.intrinsics.IntrinsicMethods
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.debugger.core.breakpoints.isInlineOnly
import org.jetbrains.kotlin.idea.debugger.core.stepping.getLineRange
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.load.java.isFromJava
import org.jetbrains.kotlin.platform.jvm.JdkPlatform
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.calls.util.getParentCall
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.isInlineClassType
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.synthetic.SyntheticJavaPropertyDescriptor
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
        if (checkLineRangeFits(lambdaExpression.getLineRange())) {
            recordFunction(lambdaExpression.functionLiteral)
        }
    }

    override fun visitNamedFunction(function: KtNamedFunction) {
        if (checkLineRangeFits(function.getLineRange()) && !recordFunction(function)) {
            super.visitNamedFunction(function)
        }
    }

    override fun visitCallableReferenceExpression(expression: KtCallableReferenceExpression) {
        if (checkLineRangeFits(expression.getLineRange())) {
            recordCallableReference(expression)
        }
        super.visitCallableReferenceExpression(expression)
    }

    private fun recordCallableReference(expression: KtCallableReferenceExpression) {
        val bindingContext = expression.analyze(BodyResolveMode.PARTIAL)
        val resolvedCall = expression.callableReference.getResolvedCall(bindingContext) ?: return
        when (val descriptor = resolvedCall.resultingDescriptor) {
            is FunctionDescriptor -> recordFunctionReference(expression, descriptor)
            is PropertyDescriptor -> recordProperty(expression, descriptor, bindingContext)
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

    private fun recordProperty(expression: KtExpression, descriptor: PropertyDescriptor, bindingContext: BindingContext) {
        if (descriptor is SyntheticJavaPropertyDescriptor) {
            val functionDescriptor =
                when ((expression as? KtNameReferenceExpression)?.computeTargetType()) {
                    KtNameReferenceExpressionUsage.PROPERTY_GETTER, KtNameReferenceExpressionUsage.UNKNOWN, null -> descriptor.getMethod
                    KtNameReferenceExpressionUsage.PROPERTY_SETTER -> descriptor.setMethod
                }
            val declaration = functionDescriptor?.let { DescriptorToSourceUtilsIde.getAnyDeclaration(element.project, it) }
            if (declaration is PsiMethod) {
                append(MethodSmartStepTarget(declaration, null, expression, true, lines))
            }
            return
        }
        val propertyDescriptor =
            when ((expression as? KtNameReferenceExpression)?.computeTargetType()) {
                KtNameReferenceExpressionUsage.PROPERTY_GETTER, KtNameReferenceExpressionUsage.UNKNOWN, null -> descriptor.getter
                KtNameReferenceExpressionUsage.PROPERTY_SETTER -> descriptor.setter
            }
        if (propertyDescriptor == null || propertyDescriptor.isDefault) return

        val ktDeclaration = DescriptorToSourceUtilsIde.getAnyDeclaration(element.project, propertyDescriptor) as? KtDeclaration ?: return

        val delegatedResolvedCall = bindingContext[BindingContext.DELEGATED_PROPERTY_RESOLVED_CALL, propertyDescriptor]
        if (delegatedResolvedCall != null) {
            val delegatedPropertyDescriptor = delegatedResolvedCall.resultingDescriptor
            val delegateDeclaration = DescriptorToSourceUtilsIde.getAnyDeclaration(
                element.project, delegatedPropertyDescriptor
            ) as? KtDeclarationWithBody ?: return
            val label = "${descriptor.name}." + KotlinMethodSmartStepTarget.calcLabel(delegatedPropertyDescriptor)
            appendPropertyFilter(delegatedPropertyDescriptor, delegateDeclaration, label, expression, lines)
        } else {
            if (ktDeclaration is KtPropertyAccessor && ktDeclaration.hasBody()) {
                val label = KotlinMethodSmartStepTarget.calcLabel(propertyDescriptor)
                appendPropertyFilter(propertyDescriptor, ktDeclaration, label, expression, lines)
            }
        }
    }

    private fun KtNameReferenceExpression.computeTargetType(): KtNameReferenceExpressionUsage {
        val potentialLeftHandSide = parent as? KtQualifiedExpression ?: this
        val binaryExpr =
            potentialLeftHandSide.parent as? KtBinaryExpression ?: return KtNameReferenceExpressionUsage.PROPERTY_GETTER
        return when (binaryExpr.operationToken) {
            KtTokens.EQ -> KtNameReferenceExpressionUsage.PROPERTY_SETTER
            else -> KtNameReferenceExpressionUsage.UNKNOWN
        }
    }

    private enum class KtNameReferenceExpressionUsage {
        PROPERTY_GETTER, PROPERTY_SETTER, UNKNOWN
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
                    methodDescriptor.name.asString(),
                    (methodDescriptor as? FunctionDescriptor)?.isSuspend ?: false
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
        if (checkLineRangeFits(expression.getLineRange())) {
            recordFunctionCall(expression)
        }
    }

    override fun visitUnaryExpression(expression: KtUnaryExpression) {
        super.visitUnaryExpression(expression)
        if (checkLineRangeFits(expression.getLineRange())) {
            recordFunctionCall(expression.operationReference)
        }
    }

    override fun visitBinaryExpression(expression: KtBinaryExpression) {
        super.visitBinaryExpression(expression)
        if (checkLineRangeFits(expression.getLineRange())) {
            recordFunctionCall(expression.operationReference)
        }
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        val calleeExpression = expression.calleeExpression
        if (calleeExpression != null && checkLineRangeFits(expression.getLineRange())) {
            recordFunctionCall(calleeExpression)
        }
        super.visitCallExpression(expression)
    }

    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
        if (checkLineRangeFits(expression.getLineRange())) {
            val bindingContext = expression.analyze(BodyResolveMode.PARTIAL)
            val resolvedCall = expression.getResolvedCall(bindingContext) ?: return
            val propertyDescriptor = resolvedCall.resultingDescriptor as? PropertyDescriptor ?: return
            recordProperty(expression, propertyDescriptor, bindingContext)
        }
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

    private fun checkLineRangeFits(lineRange: IntRange?): Boolean =
        lineRange != null && lines.isWithin(lineRange.first) && lines.isWithin(lineRange.last)
}

private fun PropertyAccessorDescriptor.getJvmMethodName(): String =
    DescriptorUtils.getJvmName(this) ?: when (this) {
        is PropertySetterDescriptor -> JvmAbi.setterName(correspondingProperty.name.asString())
        else -> JvmAbi.getterName(correspondingProperty.name.asString())
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
    return !type.isFunctionType && !type.isSuspendFunctionType && type is SimpleType && type.isSingleClassifierType
}

private fun KtFunction.getParameterAndResolvedCallDescriptor(): Pair<ValueParameterDescriptor, CallableMemberDescriptor>? {
    val context = analyze()
    val resolvedCall = getParentCall(context).getResolvedCall(context) ?: return null
    val descriptor = resolvedCall.resultingDescriptor as? CallableMemberDescriptor ?: return null
    val arguments = resolvedCall.valueArguments

    for ((param, argument) in arguments) {
        if (argument.arguments.any { it.getFunctionLiteral() == this }) {
            return Pair(param, descriptor)
        }
    }
    return null
}

private fun ValueArgument.getFunctionLiteral(): KtFunction? {
    val argumentExpression = getArgumentExpression()
    if (argumentExpression is KtFunction) {
        return argumentExpression
    }
    return argumentExpression?.unpackFunctionLiteral()?.functionLiteral
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
