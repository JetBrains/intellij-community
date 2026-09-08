// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.diagnostics.diagnostics
import org.jetbrains.kotlin.analysis.api.diagnostics.KaSeverity
import org.jetbrains.kotlin.analysis.api.renderer.render
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.resolution.resolveSuccessfulSymbol
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.analysis.api.types.KaSubtypingErrorTypePolicy
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.isSubtypeOf
import org.jetbrains.kotlin.analysis.api.types.type
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeInfoBase
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.types.Variance


internal sealed interface CallableReferenceFix {
    /** Reference stays valid as is. */
    object None : CallableReferenceFix

    /**
     * Replace the reference with `<prefix>::name`. The prefix may be a value expression
     * (`c::fn`, bound - type preserved), a type (`Ctx::fn`, unbound - best effort for
     * references that were already unresolvable), or empty (qualifier dropped).
     */
    data class SetQualifier(val prefix: String) : CallableReferenceFix

    /** Replace the reference with an adapter lambda of the old reference's shape. */
    data class ConvertToLambda(val lambdaText: String) : CallableReferenceFix

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
        val newText = when (val fix = fix) {
            is CallableReferenceFix.SetQualifier -> "${fix.prefix}::${expression.callableReference.text}"
            is CallableReferenceFix.ConvertToLambda -> fix.lambdaText
            is CallableReferenceFix.Conflict, CallableReferenceFix.None -> return null
        }
        return expression.replace(KtPsiFactory(element.project).createExpression(newText)) as KtElement
    }
}

private const val CONTEXT_PARAMS_CONFLICT = "change.signature.conflict.callable.reference.context.parameters"
private const val MEMBER_EXTENSION_CONFLICT = "change.signature.conflict.callable.reference.member.extension"

context(session: KaSession)
private fun classify(
    expression: KtCallableReferenceExpression,
    changeInfo: KotlinChangeInfoBase,
): CallableReferenceFix {
    val receiverInfo = changeInfo.receiverParameterInfo
    val receiverAdded = receiverInfo != null && changeInfo.oldReceiverInfo == null
    val receiverRemoved = receiverInfo == null && changeInfo.oldReceiverInfo != null
    val receiverRetyped = changeInfo.isReceiverTypeChanged() && !receiverAdded && !receiverRemoved
    val qualified = expression.receiverExpression != null
    val isMember = isMemberCallable(changeInfo)

    return when {
        // Value param(s) became context param(s): a reference can't pass context arguments.
        anyValueParamBecameContext(changeInfo) ->
            if (!qualified && !isMember && isOnlyParamKindChange(changeInfo))
                buildContextArgsLambda(expression, changeInfo)
            else
                conflictFix(expression, CONTEXT_PARAMS_CONFLICT)

        contextParameterBecameReceiver(changeInfo) -> when {
            qualified ->
                conflictFix(expression, if (isMember) MEMBER_EXTENSION_CONFLICT else CONTEXT_PARAMS_CONFLICT)
            isMember ->
                buildMemberExtensionLambda(expression, changeInfo)
                    ?: conflictFix(expression, MEMBER_EXTENSION_CONFLICT)
            else ->
                CallableReferenceFix.SetQualifier(
                    boundReceiverText(expression, changeInfo) ?: fullyQualifiedReceiverType(changeInfo)
                )
        }

        // Any other receiver introduction: qualified references (`this::fn`, `foo::fn`),
        // a brand-new receiver, a non-first value parameter - no safe rewrite yet.
        receiverAdded ->
            conflictFix(expression, if (isMember) MEMBER_EXTENSION_CONFLICT else CONTEXT_PARAMS_CONFLICT)

        // Unbound Type::fn follows receiver retype (new type) or removal (empty prefix).
        (receiverRetyped || receiverRemoved) && qualified && isUnboundTypeQualifier(expression) ->
            CallableReferenceFix.SetQualifier(if (receiverInfo != null) fullyQualifiedReceiverType(changeInfo) else "")

        else -> CallableReferenceFix.None
    }
}

/** The change touches nothing but flipping value params to context params. */
private fun isOnlyParamKindChange(changeInfo: KotlinChangeInfoBase): Boolean =
    changeInfo.receiverParameterInfo == null && changeInfo.oldReceiverInfo == null &&
            changeInfo.newParameters.none { it.isNewParameter || it.wasContextParameter }

private fun anyValueParamBecameContext(changeInfo: KotlinChangeInfoBase): Boolean =
    changeInfo.newParameters.any { !it.isNewParameter && !it.wasContextParameter && it.isContextParameter }

private fun contextParameterBecameReceiver(changeInfo: KotlinChangeInfoBase): Boolean =
    changeInfo.oldReceiverInfo == null && changeInfo.receiverParameterInfo?.wasContextParameter == true

private fun isMemberCallable(changeInfo: KotlinChangeInfoBase): Boolean =
    (changeInfo.method.unwrapped as? KtCallableDeclaration)?.containingClassOrObject != null

/** value param -> context param: `::fn` used as `(Ctx, X) -> R` becomes `{ ctx, x -> context(ctx) { fn(x) } }`. */
private fun buildContextArgsLambda(
    expression: KtCallableReferenceExpression,
    changeInfo: KotlinChangeInfoBase,
): CallableReferenceFix.ConvertToLambda {
    // Lambda parameters = ALL old parameters in old order (the old function type shape).
    val allParams = changeInfo.newParameters.sortedBy { it.oldIndex }
    val contextArgs = allParams.filter { it.isContextParameter }.joinToString(", ") { it.name }
    val valueArgs = allParams.filter { !it.isContextParameter }.joinToString(", ") { it.name }
    val call = "${calleeName(expression, changeInfo)}($valueArgs)"
    val body = if (contextArgs.isEmpty()) call else "context($contextArgs) { $call }"
    return CallableReferenceFix.ConvertToLambda(lambdaText(allParams.map { it.name }, body))
}

/** context param -> receiver on a member: `::fn` becomes `{ x -> c.fn(x) }`; null when receivers are unreachable. */
context(session: KaSession)
private fun buildMemberExtensionLambda(
    expression: KtCallableReferenceExpression,
    changeInfo: KotlinChangeInfoBase,
): CallableReferenceFix.ConvertToLambda? {
    val method = changeInfo.method.unwrapped as? KtCallableDeclaration ?: return null
    val containingClass = method.containingClassOrObject ?: return null
    // Dispatch receiver is implicit this@Class, so the usage must be lexically inside the class.
    if (!PsiTreeUtil.isAncestor(containingClass, expression, true)) return null
    val receiverText = boundReceiverText(expression, changeInfo) ?: return null

    val valueParams = changeInfo.newParameters
        .filter { !it.isNewParameter && !it.wasContextParameter && it != changeInfo.receiverParameterInfo }
        .sortedBy { it.oldIndex }
        .map { it.name }
    val body = "$receiverText.${calleeName(expression, changeInfo)}(${valueParams.joinToString(", ")})"
    return CallableReferenceFix.ConvertToLambda(lambdaText(valueParams, body))
}

/**
 * An expression yielding the context value that satisfied the removed context parameter at the
 * usage site. Only meaningful when the reference is resolved before the change: then a context
 * argument is guaranteed to exist, so a named context parameter of a suitable subtype is used
 * directly, and `contextOf<T>()` covers the rest (anonymous parameters, `context(...)` blocks).
 * Null when the reference was already unresolvable.
 */
@OptIn(KaExperimentalApi::class)
context(session: KaSession)
private fun boundReceiverText(
    expression: KtCallableReferenceExpression,
    changeInfo: KotlinChangeInfoBase,
): String? {
    if (!isResolvable(expression)) return null

    val receiverInfo = changeInfo.receiverParameterInfo ?: return null
    val method = changeInfo.method.unwrapped as? KtCallableDeclaration ?: return null
    val receiverType: KaType = method.contextParametersOrNull()
        ?.getOrNull(receiverInfo.oldIndex)?.symbol?.returnType ?: return null

    val namedInScope = expression.parents
        .filterIsInstance<KtCallableDeclaration>()
        .flatMap { it.contextParametersOrNull().orEmpty() }
        .firstOrNull { !isAnonymousParameter(it) && it.symbol.returnType.isSubtypeOf(receiverType, KaSubtypingErrorTypePolicy.STRICT) }
    return namedInScope?.name
        ?: "contextOf<${receiverType.render(KaTypeRendererForSource.WITH_QUALIFIED_NAMES, position = Variance.INVARIANT)}>()"
}

/** True when the callable reference resolves without errors in the pre-change code. */

@OptIn(KaExperimentalApi::class)
context(session: KaSession)
private fun isResolvable(expression: KtCallableReferenceExpression): Boolean =
    expression.diagnostics().none { it.severity == KaSeverity.ERROR } &&
            expression.callableReference.resolveSuccessfulSymbol() != null

@OptIn(KaExperimentalApi::class)
private fun fullyQualifiedReceiverType(changeInfo: KotlinChangeInfoBase): String {
    val receiverInfo = changeInfo.receiverParameterInfo ?: return ""
    val context = changeInfo.method.unwrapped as? KtElement ?: return receiverInfo.typeText
    val typeRef = KtPsiFactory(context.project)
        .createTypeCodeFragment(receiverInfo.typeText, context)
        .getContentElement()
        ?: return receiverInfo.typeText
    // The code fragment lives in its own (dangling file) module -> needs its own session.
    return analyze(typeRef) {
        typeRef.type.render(KaTypeRendererForSource.WITH_QUALIFIED_NAMES, position = Variance.INVARIANT)
    }
}

@OptIn(KaExperimentalApi::class)
context(session: KaSession)
private fun isUnboundTypeQualifier(expression: KtCallableReferenceExpression): Boolean {
    val nameExpression = when (val receiver = expression.receiverExpression) {
        is KtSimpleNameExpression -> receiver
        is KtDotQualifiedExpression -> receiver.selectorExpression as? KtSimpleNameExpression
        else -> null
    } ?: return false
    return nameExpression.resolveSuccessfulSymbol() is KaClassLikeSymbol
}

private fun KtCallableDeclaration.contextParametersOrNull(): List<KtParameter>? =
    takeIf { this is KtNamedFunction || this is KtProperty }?.contextParameters

/** True if the parameter name consists only of underscores (anonymous context parameter). */
private fun isAnonymousParameter(parameter: KtParameter): Boolean =
    parameter.name.let { name -> !name.isNullOrEmpty() && name.all { it == '_' } }

private fun conflictFix(expression: KtCallableReferenceExpression, messageKey: String): CallableReferenceFix.Conflict =
    CallableReferenceFix.Conflict(KotlinBundle.message(messageKey, expression.text))

private fun calleeName(expression: KtCallableReferenceExpression, changeInfo: KotlinChangeInfoBase): String =
    changeInfo.newName ?: expression.callableReference.text

private fun lambdaText(parameterNames: List<String>, body: String): String =
    if (parameterNames.isEmpty()) "{ $body }"
    else "{ ${parameterNames.joinToString(", ")} -> $body }"