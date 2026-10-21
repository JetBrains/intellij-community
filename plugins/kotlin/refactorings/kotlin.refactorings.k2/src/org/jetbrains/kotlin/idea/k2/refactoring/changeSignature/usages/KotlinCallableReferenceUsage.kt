// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages

import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.diagnostics.KaSeverity
import org.jetbrains.kotlin.analysis.api.diagnostics.diagnostics
import org.jetbrains.kotlin.analysis.api.renderer.render
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.resolution.resolveSuccessfulCall
import org.jetbrains.kotlin.analysis.api.resolution.resolveSuccessfulSymbol
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.type
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeInfoBase
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinParameterInfo
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages.CallableReferenceFix.Conflict
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages.CallableReferenceFix.None
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages.CallableReferenceFix.SetQualifier
import org.jetbrains.kotlin.idea.k2.refactoring.util.createReplacementForContextArgument
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.types.Variance


internal sealed interface CallableReferenceFix {
    /** Reference stays valid as is. */
    object None : CallableReferenceFix

    /** Replaces the qualifier. An empty prefix removes it. */
    data class SetQualifier(val prefix: String) : CallableReferenceFix

    /** No safe rewrite; reported via the conflicts dialog, reference left untouched. */
    data class Conflict(val message: String) : CallableReferenceFix
}

internal class KotlinCallableReferenceUsage(expression: KtCallableReferenceExpression, internal val fix: CallableReferenceFix) :
    UsageInfo(expression),
    KotlinBaseChangeSignatureUsage {

    companion object {
        context(session: KaSession)
        fun create(
            expression: KtCallableReferenceExpression,
            changeInfo: KotlinChangeInfoBase,
        ): KotlinCallableReferenceUsage =
            KotlinCallableReferenceUsage(expression, classify(expression, changeInfo))
    }

    override fun processUsage(
        changeInfo: KotlinChangeInfoBase,
        element: KtElement,
        allUsages: Array<out UsageInfo>
    ): KtElement? {
        val expression = element as? KtCallableReferenceExpression ?: return null
        val newName = if (changeInfo.isNameChanged) changeInfo.newName else expression.callableReference.text
        val newText = when (val fix = fix) {
            is SetQualifier -> "${fix.prefix}::${newName}"
            None -> {
                if (!changeInfo.isNameChanged) return null
                val qualifier = expression.receiverExpression?.text.orEmpty()
                "${qualifier}::${newName}"
            }
            is Conflict -> return null
        }
        return expression.replace(KtPsiFactory(element.project).createExpression(newText)) as KtElement
    }
}

context(session: KaSession)
private fun classify(
    expression: KtCallableReferenceExpression,
    changeInfo: KotlinChangeInfoBase,
): CallableReferenceFix {
    // One signature change can contain multiple conversions. A conflict must win over a partial rewrite.
    val fixes = paramConversions(changeInfo).map { classifyConversion(it, expression, changeInfo) }
    return fixes.firstOrNull { it is Conflict }
        ?: fixes.firstOrNull { it is SetQualifier }
        ?: None
}

private const val PARAMETER_TO_CONTEXT_CONFLICT = "change.signature.conflict.callable.reference.parameter.to.context"
private const val RECEIVER_TO_CONTEXT_CONFLICT = "change.signature.conflict.callable.reference.receiver.to.context"
private const val TYPE_CHANGE_CONFLICT = "change.signature.conflict.callable.reference.type.change"
private const val MEMBER_EXTENSION_CONFLICT = "change.signature.conflict.callable.reference.member.extension"
private const val QUALIFIER_OCCUPIED_CONFLICT = "change.signature.conflict.callable.reference.qualifier.occupied"
private const val BOUND_RECEIVER_LOST_CONFLICT = "change.signature.conflict.callable.reference.bound.receiver.lost"
private const val CONTEXT_PARAMETER_UNAVAILABLE = "change.signature.conflict.callable.reference.context.argument.unavailable"

private enum class ParamConversion {
    VALUE_TO_CONTEXT,
    CONTEXT_TO_VALUE,
    CONTEXT_TO_RECEIVER,
    RECEIVER_TO_CONTEXT,
    VALUE_TO_RECEIVER,
    RECEIVER_TO_VALUE,
    RECEIVER_ADDED,
    RECEIVER_REMOVED,
    RECEIVER_RETYPED,
    PARAMETERS_ADDED,
    PARAMETERS_REORDERED,
    PARAMETERS_REMOVED,
    PARAMETERS_RETYPED,
    RETURN_TYPE_CHANGED,
}

private fun KotlinParameterInfo?.isSameOriginalParameterAs(other: KotlinParameterInfo?): Boolean =
    this != null && other != null && !isNewParameter && !other.isNewParameter && oldIndex == other.oldIndex

private fun paramConversions(changeInfo: KotlinChangeInfoBase): List<ParamConversion> {
    val receiverInfo = changeInfo.receiverParameterInfo
    val oldReceiverInfo = changeInfo.oldReceiverInfo
    val receiverKept = receiverInfo.isSameOriginalParameterAs(oldReceiverInfo)
    val receiverAppeared = receiverInfo != null && !receiverKept
    val oldReceiverAsParam = oldReceiverInfo?.let { old ->
        changeInfo.newParameters.firstOrNull {
            it.isSameOriginalParameterAs(old) && !it.isSameOriginalParameterAs(receiverInfo)
        }
    }

    val contextToReceiver = receiverAppeared && receiverInfo.wasContextParameter
    val valueToReceiver =
        receiverAppeared && !receiverInfo.isNewParameter && !receiverInfo.wasContextParameter
    val receiverAdded = receiverAppeared && receiverInfo.isNewParameter
    val receiverToContext = oldReceiverAsParam?.isContextParameter == true
    val receiverToValue = oldReceiverAsParam?.isContextParameter == false
    val receiverRemoved = oldReceiverInfo != null && !receiverKept && oldReceiverAsParam == null
    val receiverRetyped = receiverKept && changeInfo.isReceiverTypeChanged()

    val valueToContext = changeInfo.newParameters.any {
        !it.isNewParameter && !it.wasContextParameter && it.isContextParameter && !it.isSameOriginalParameterAs(oldReceiverInfo)
    }
    val contextToValue = changeInfo.newParameters.any {
        !it.isNewParameter && it.wasContextParameter && !it.isContextParameter && it != receiverInfo
    }
    val parametersAdded = changeInfo.newParameters.any { it.isNewParameter && it != receiverInfo }
    // Compare only unchanged value parameters. Other movements are classified separately.
    val parametersReordered = changeInfo.newParameters
        .filter {
            !it.isNewParameter && !it.isContextParameter && !it.wasContextParameter &&
                    it != receiverInfo && !it.isSameOriginalParameterAs(oldReceiverInfo)
        }
        .map { it.oldIndex }
        .let { indexes -> indexes != indexes.sorted() }
    val parametersRemoved = changeInfo.isValueParameterRemoved()
    val parametersRetyped = changeInfo.isValueParameterRetyped()
    val returnTypeChanged = changeInfo.isReturnTypeChanged
    return listOfNotNull(
        ParamConversion.VALUE_TO_CONTEXT.takeIf { valueToContext },
        ParamConversion.CONTEXT_TO_VALUE.takeIf { contextToValue },
        ParamConversion.CONTEXT_TO_RECEIVER.takeIf { contextToReceiver },
        ParamConversion.RECEIVER_TO_CONTEXT.takeIf { receiverToContext },
        ParamConversion.VALUE_TO_RECEIVER.takeIf { valueToReceiver },
        ParamConversion.RECEIVER_TO_VALUE.takeIf { receiverToValue },
        ParamConversion.RECEIVER_ADDED.takeIf { receiverAdded },
        ParamConversion.RECEIVER_REMOVED.takeIf { receiverRemoved },
        ParamConversion.RECEIVER_RETYPED.takeIf { receiverRetyped },
        ParamConversion.PARAMETERS_ADDED.takeIf { parametersAdded },
        ParamConversion.PARAMETERS_REORDERED.takeIf { parametersReordered },
        ParamConversion.PARAMETERS_REMOVED.takeIf { parametersRemoved },
        ParamConversion.PARAMETERS_RETYPED.takeIf { parametersRetyped },
        ParamConversion.RETURN_TYPE_CHANGED.takeIf { returnTypeChanged },
    )
}

context(session: KaSession)
private fun classifyConversion(
    conversion: ParamConversion,
    expression: KtCallableReferenceExpression,
    changeInfo: KotlinChangeInfoBase,
): CallableReferenceFix {
    val qualified = expression.receiverExpression != null

    return when (conversion) {
        ParamConversion.CONTEXT_TO_RECEIVER -> when {
            isMemberCallable(changeInfo) -> conflictFix(expression, MEMBER_EXTENSION_CONFLICT)
            qualified -> conflictFix(expression, QUALIFIER_OCCUPIED_CONFLICT)
            else -> {
                val boundReceiverText = boundReceiverText(expression, changeInfo)
                if (boundReceiverText != null) {
                    SetQualifier(boundReceiverText)
                } else {
                    conflictFix(expression, CONTEXT_PARAMETER_UNAVAILABLE)
                }
            }
        }

        ParamConversion.VALUE_TO_RECEIVER -> when {
            isMemberCallable(changeInfo) -> conflictFix(expression, MEMBER_EXTENSION_CONFLICT)
            qualified -> conflictFix(expression, QUALIFIER_OCCUPIED_CONFLICT)
            // `(X, Y) -> R` and `X.(Y) -> R` have the same shape only when `X` is first.
            changeInfo.receiverParameterInfo?.oldIndex != 0 -> conflictFix(expression, TYPE_CHANGE_CONFLICT)
            else -> receiverTypeQualifier(expression, changeInfo)
        }

        ParamConversion.RECEIVER_ADDED ->
            if (isResolvable(expression)) conflictFix(expression, TYPE_CHANGE_CONFLICT) else None

        ParamConversion.RECEIVER_TO_CONTEXT ->
            conflictFix(expression, RECEIVER_TO_CONTEXT_CONFLICT)

        ParamConversion.RECEIVER_TO_VALUE -> when {
            !changeInfo.newParameters.firstOrNull { !it.isContextParameter }
                .isSameOriginalParameterAs(changeInfo.oldReceiverInfo) ->
                conflictFix(expression, TYPE_CHANGE_CONFLICT)

            isUnboundTypeQualifier(expression) -> SetQualifier("")
            qualified || isResolvable(expression) -> conflictFix(expression, BOUND_RECEIVER_LOST_CONFLICT)
            else -> None
        }
        ParamConversion.RECEIVER_REMOVED -> when {
            isUnboundTypeQualifier(expression) ->
                if (isResolvable(expression)) {
                    conflictFix(expression, TYPE_CHANGE_CONFLICT)
                } else {
                    None
                }
            qualified -> conflictFix(expression, BOUND_RECEIVER_LOST_CONFLICT)
            else -> None
        }

        ParamConversion.RECEIVER_RETYPED -> when {
            isUnboundTypeQualifier(expression) -> receiverTypeQualifier(expression, changeInfo)
            qualified || isResolvable(expression) -> conflictFix(expression, TYPE_CHANGE_CONFLICT)
            else -> None
        }

        ParamConversion.VALUE_TO_CONTEXT ->
            conflictFix(expression, PARAMETER_TO_CONTEXT_CONFLICT, convertedToContextParameterName(changeInfo))

        ParamConversion.CONTEXT_TO_VALUE ->
            if (isResolvable(expression)) conflictFix(expression, TYPE_CHANGE_CONFLICT) else None

        ParamConversion.PARAMETERS_ADDED, ParamConversion.PARAMETERS_REORDERED, ParamConversion.PARAMETERS_REMOVED,
        ParamConversion.PARAMETERS_RETYPED, ParamConversion.RETURN_TYPE_CHANGED ->
            if (isResolvable(expression)) conflictFix(expression, TYPE_CHANGE_CONFLICT) else None
    }
}

private fun receiverTypeQualifier(
    expression: KtCallableReferenceExpression,
    changeInfo: KotlinChangeInfoBase,
): CallableReferenceFix {
    val qualifier = fullyQualifiedReceiverType(changeInfo)
    return if (qualifier != null) SetQualifier(qualifier) else conflictFix(expression, TYPE_CHANGE_CONFLICT)
}

private fun fullyQualifiedReceiverType(changeInfo: KotlinChangeInfoBase): String? {
    val receiverInfo = changeInfo.receiverParameterInfo ?: return null
    val context = changeInfo.method.unwrapped as? KtElement ?: return null
    val typeRef = KtPsiFactory(context.project)
        .createTypeCodeFragment(receiverInfo.typeText, context)
        .getContentElement()
        ?: return null
    // The code fragment lives in its own (dangling file) module -> needs its own session.
    return analyze(typeRef) {
        val type = typeRef.type
        if (!type.isSafeQualifierType()) return@analyze null
        type.render(KaTypeRendererForSource.WITH_QUALIFIED_NAMES, position = Variance.INVARIANT)
    }
}

private fun KaType.isSafeQualifierType(): Boolean {
    if (this !is KaClassType) return false
    if (this is KaFunctionType) return false
    return typeArguments.all { argument ->
        val argumentType = argument.type ?: return@all true
        argumentType.isSafeQualifierType()
    }
}

/** Returns text for the context argument selected by call resolution. */
context(session: KaSession)
private fun boundReceiverText(
    expression: KtCallableReferenceExpression,
    changeInfo: KotlinChangeInfoBase,
): String? {
    if (!isResolvable(expression)) return null

    val receiverInfo = changeInfo.receiverParameterInfo ?: return null
    val contextArgument = expression.resolveSuccessfulCall()
        ?.contextArguments
        ?.getOrNull(receiverInfo.oldIndex)
        ?: return null

    return createReplacementForContextArgument(contextArgument)
}

/**
 * Symbol resolution can succeed even when the complete expression has an invalid function type.
 */
context(session: KaSession)
private fun isResolvable(expression: KtCallableReferenceExpression): Boolean =
    expression.diagnostics().none { it.severity == KaSeverity.ERROR } &&
            expression.callableReference.resolveSuccessfulSymbol() != null

private fun convertedToContextParameterName(changeInfo: KotlinChangeInfoBase): String =
    changeInfo.newParameters.first {
        !it.isNewParameter && !it.wasContextParameter && it.isContextParameter &&
                !it.isSameOriginalParameterAs(changeInfo.oldReceiverInfo)
    }.name

private fun isMemberCallable(changeInfo: KotlinChangeInfoBase): Boolean =
    (changeInfo.method.unwrapped as? KtCallableDeclaration)?.containingClassOrObject != null

private fun qualifierName(receiver: KtExpression?): KtSimpleNameExpression? = when (receiver) {
    is KtSimpleNameExpression -> receiver
    is KtDotQualifiedExpression -> qualifierName(receiver.selectorExpression)
    is KtCallExpression ->
        if (receiver.valueArgumentList == null &&
            receiver.lambdaArguments.isEmpty()
        ) {
            receiver.calleeExpression as? KtSimpleNameExpression
        } else {
            null
        }

    else -> null
}

context(session: KaSession)
private fun isUnboundTypeQualifier(expression: KtCallableReferenceExpression): Boolean {
    val nameExpression = qualifierName(expression.receiverExpression) ?: return false
    val symbol = nameExpression.resolveSuccessfulSymbol()
    if (symbol is KaNamedClassSymbol &&
        symbol.classKind == KaClassKind.OBJECT
    ) {
        return false
    }
    return symbol is KaClassLikeSymbol
}

private fun conflictFix(expression: KtCallableReferenceExpression, messageKey: String, vararg args: Any): Conflict =
    Conflict(KotlinBundle.message(messageKey, CommonRefactoringUtil.htmlEmphasize(expression.text), *args))
