// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.diagnostics.KaSeverity
import org.jetbrains.kotlin.analysis.api.diagnostics.diagnostics
import org.jetbrains.kotlin.analysis.api.expressions.expectedType
import org.jetbrains.kotlin.analysis.api.renderer.render
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.resolution.resolveSuccessfulSymbol
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaSubtypingErrorTypePolicy
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.isSubtypeOf
import org.jetbrains.kotlin.analysis.api.types.type
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeInfoBase
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinParameterInfo
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtThisExpression
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

context(session: KaSession)
private fun classify(
    expression: KtCallableReferenceExpression,
    changeInfo: KotlinChangeInfoBase,
): CallableReferenceFix {
    val conversion = paramConversion(changeInfo)
    if (conversion == ParamConversion.NONE) return classifyNonContextChange(expression, changeInfo)

    val qualified = expression.receiverExpression != null
    val isMember = isMemberCallable(changeInfo)

    val fix: CallableReferenceFix? = when (conversion) {
        // Requires a resolved reference: for unresolvable ones (e.g. a contextual callee without
        // -Xcallable-references-to-contextual in scope) we conservatively report a conflict
        // rather than guess an adapter.
        ParamConversion.VALUE_TO_CONTEXT ->
            if (!qualified && !isMember && isResolvable(expression) && isUsedAsFunctionType(expression))
                buildContextArgsLambda(expression, changeInfo) else null

        // Reference loses implicit context: the adapter reuses the resolved context argument,
        // so the reference must have resolved before the change.
        ParamConversion.CONTEXT_TO_VALUE ->
            if (!qualified && isResolvable(expression) && isUsedAsFunctionType(expression))
                buildExplicitContextArgsLambda(expression, changeInfo) else null

        // Reference type preserved by qualifying: c::fn / contextOf<T>()::fn.
        // No isResolvable gate: unresolvable references get the unbound type qualifier (best effort).
        ParamConversion.CONTEXT_TO_RECEIVER -> when {
            qualified -> null
            isMember -> buildMemberExtensionLambda(expression, changeInfo)
            else -> CallableReferenceFix.SetQualifier(
                boundReceiverText(expression, changeInfo) ?: fullyQualifiedReceiverType(changeInfo)
            )
        }

        // Receiver absorbed into context: the qualifier's value moves into context(...),
        // which reuses its resolved meaning -> resolvability required.
        ParamConversion.RECEIVER_TO_CONTEXT ->
            if (qualified && !isMember && isResolvable(expression) && isUsedAsFunctionType(expression))
                buildReceiverToContextLambda(expression, changeInfo) else null

        ParamConversion.MIXED, ParamConversion.NONE -> null
    }

    return fix ?: conflictFix(expression, conflictMessageKey(conversion, isMember))
}

private const val CONTEXT_PARAMS_CONFLICT = "change.signature.conflict.callable.reference.context.parameters"
private const val MEMBER_EXTENSION_CONFLICT = "change.signature.conflict.callable.reference.member.extension"

private fun conflictMessageKey(conversion: ParamConversion, isMember: Boolean): String =
    if (isMember && (conversion == ParamConversion.CONTEXT_TO_RECEIVER || conversion == ParamConversion.RECEIVER_TO_CONTEXT))
        MEMBER_EXTENSION_CONFLICT
    else CONTEXT_PARAMS_CONFLICT

private enum class ParamConversion {
    VALUE_TO_CONTEXT,      // ConvertRegularParameterToContextParameterIntention
    CONTEXT_TO_VALUE,      // ConvertContextParameterToRegularParameterIntention
    CONTEXT_TO_RECEIVER,   // ConvertContextParameterToReceiverIntention
    RECEIVER_TO_CONTEXT,   // ConvertReceiverParameterToContextParameterIntention
    MIXED,                 // more than one of the above / other structural changes on top
    NONE,                  // no param-kind change (rename, receiver retype/removal, ...)
}

private fun paramConversion(changeInfo: KotlinChangeInfoBase): ParamConversion {
    val receiverInfo = changeInfo.receiverParameterInfo
    val valueToContext = changeInfo.newParameters.any {
        !it.isNewParameter && !it.wasContextParameter && it.isContextParameter &&
                it != changeInfo.oldReceiverInfo
    }
    val contextToValue = changeInfo.newParameters.any {
        !it.isNewParameter && it.wasContextParameter && !it.isContextParameter && it != receiverInfo
    }
    val contextToReceiver = changeInfo.oldReceiverInfo == null && receiverInfo?.wasContextParameter == true
    val receiverToContext = changeInfo.oldReceiverInfo != null && receiverInfo == null &&
            changeInfo.newParameters.any { it == changeInfo.oldReceiverInfo && it.isContextParameter }

    val kinds = listOfNotNull(
        ParamConversion.VALUE_TO_CONTEXT.takeIf { valueToContext },
        ParamConversion.CONTEXT_TO_VALUE.takeIf { contextToValue },
        ParamConversion.CONTEXT_TO_RECEIVER.takeIf { contextToReceiver },
        ParamConversion.RECEIVER_TO_CONTEXT.takeIf { receiverToContext },
    )
    return when {
        kinds.size > 1 -> ParamConversion.MIXED
        kinds.size == 1 && hasOtherStructuralChanges(changeInfo, kinds.single()) -> ParamConversion.MIXED
        kinds.size == 1 -> kinds.single()
        else -> ParamConversion.NONE
    }
}

context(session: KaSession)
private fun classifyNonContextChange(
    expression: KtCallableReferenceExpression,
    changeInfo: KotlinChangeInfoBase,
): CallableReferenceFix {
    val receiverAdded = changeInfo.oldReceiverInfo == null && changeInfo.receiverParameterInfo != null
    val receiverRemoved = changeInfo.oldReceiverInfo != null && changeInfo.receiverParameterInfo == null
    val receiverRetyped = changeInfo.isReceiverTypeChanged() && !receiverAdded && !receiverRemoved
    val qualified = expression.receiverExpression != null

    return when {
        // Unbound Type::fn follows receiver retype (new type) or removal (empty prefix).
        (receiverRetyped || receiverRemoved) && qualified && isUnboundTypeQualifier(expression) ->
            CallableReferenceFix.SetQualifier(
                if (changeInfo.receiverParameterInfo != null) fullyQualifiedReceiverType(changeInfo) else ""
            )
        receiverAdded ->
            conflictFix(expression, CONTEXT_PARAMS_CONFLICT)
        else -> CallableReferenceFix.None
    }
}

/** New params or receiver changes not implied by the conversion itself. */
private fun hasOtherStructuralChanges(changeInfo: KotlinChangeInfoBase, kind: ParamConversion): Boolean {
    if (changeInfo.newParameters.any { it.isNewParameter }) return true
    return when (kind) {
        ParamConversion.VALUE_TO_CONTEXT, ParamConversion.CONTEXT_TO_VALUE ->
            changeInfo.receiverParameterInfo != null || changeInfo.oldReceiverInfo != null
        else -> false
    }
}

/** value param -> context param: `::fn` used as `(Ctx, X) -> R` becomes `{ ctx, x -> context(ctx) { fn(x) } }`. */
private fun buildContextArgsLambda(
    expression: KtCallableReferenceExpression,
    changeInfo: KotlinChangeInfoBase,
): CallableReferenceFix.ConvertToLambda {
    val varargOldName = changeInfo.methodDeclaration()?.varargName()
    // Old function-type shape = old non-context parameters, in old order.
    val oldShapeParams = changeInfo.newParameters
        .filter { !it.wasContextParameter }
        .sortedBy { it.oldIndex }
    // Only the newly converted parameters move into context(...); the rest remain call arguments.
    val (convertedToContext, valueParams) = oldShapeParams.partition { it.isContextParameter }
    val call = "${calleeName(expression, changeInfo)}(${valueParams.joinToString(", ") { renderArg(it, varargOldName) }})"
    val body =
        if (convertedToContext.isEmpty()) call
        else "context(${convertedToContext.joinToString(", ") { it.name }}) { $call }"
    return CallableReferenceFix.ConvertToLambda(lambdaText(oldShapeParams.map { it.name }, body))
}

/** context param -> value param: `::fn` used as `(X) -> R` becomes `{ x -> fn(<context>, x) }`. */
context(session: KaSession)
private fun buildExplicitContextArgsLambda(
    expression: KtCallableReferenceExpression,
    changeInfo: KotlinChangeInfoBase,
): CallableReferenceFix.ConvertToLambda? {
    val method = changeInfo.methodDeclaration() ?: return null
    if (!usageInsideContainingClass(method, expression)) return null
    val varargOldName = method.varargName()

    // Lambda parameters = old VALUE parameters in old order (the old function type shape).
    val lambdaParams = changeInfo.newParameters
        .filter { !it.wasContextParameter }
        .sortedBy { it.oldIndex }
        .map { it.name }

    // Call args in new-signature order; still-context params stay implicit,
    // former context params are passed explicitly.
    val args = changeInfo.newParameters
        .filter { !it.isContextParameter }
        .map { param ->
            if (param.wasContextParameter) {
                val contextType = method.contextParametersOrNull()
                    ?.getOrNull(param.oldIndex)?.symbol?.returnType ?: return null
                contextArgumentText(expression, contextType)
            } else renderArg(param, varargOldName)
        }
    val body = "${calleeName(expression, changeInfo)}(${args.joinToString(", ")})"
    return CallableReferenceFix.ConvertToLambda(lambdaText(lambdaParams, body))
}

/** context param -> receiver on a member: `::fn` becomes `{ x -> c.fn(x) }`; null when receivers are unreachable. */
context(session: KaSession)
private fun buildMemberExtensionLambda(
    expression: KtCallableReferenceExpression,
    changeInfo: KotlinChangeInfoBase,
): CallableReferenceFix.ConvertToLambda? {
    val method = changeInfo.methodDeclaration() ?: return null
    if (method.containingClassOrObject == null || !usageInsideContainingClass(method, expression)) return null
    val receiverText = boundReceiverText(expression, changeInfo) ?: return null

    val valueParams = changeInfo.newParameters
        .filter { !it.isNewParameter && !it.wasContextParameter && it != changeInfo.receiverParameterInfo }
        .sortedBy { it.oldIndex }
        .map { it.name }
    val body = "$receiverText.${calleeName(expression, changeInfo)}(${valueParams.joinToString(", ")})"
    return CallableReferenceFix.ConvertToLambda(lambdaText(valueParams, body))
}

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

/**
 * An expression yielding the context value that satisfied the removed context parameter at the
 * usage site. Only meaningful when the reference is resolved before the change: then a context
 * argument is guaranteed to exist, so a named context parameter of a suitable subtype is used
 * directly, and `contextOf<T>()` covers the rest (anonymous parameters, `context(...)` blocks).
 * Null when the reference was already unresolvable.
 */
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

    return contextArgumentText(expression, receiverType)
}

/** A named context parameter of a suitable subtype in scope, or `contextOf<T>()` as fallback. */
@OptIn(KaExperimentalApi::class)
context(session: KaSession)
private fun contextArgumentText(expression: KtCallableReferenceExpression, contextType: KaType): String {
    val namedInScope = expression.parents
        .filterIsInstance<KtCallableDeclaration>()
        .flatMap { it.contextParametersOrNull().orEmpty() }
        .firstOrNull { !isAnonymousParameter(it) && it.symbol.returnType.isSubtypeOf(contextType, KaSubtypingErrorTypePolicy.STRICT) }
    return namedInScope?.name
        ?: "contextOf<${contextType.render(KaTypeRendererForSource.WITH_QUALIFIED_NAMES, position = Variance.INVARIANT)}>()"
}

context(session: KaSession)
private fun buildReceiverToContextLambda(
    expression: KtCallableReferenceExpression,
    changeInfo: KotlinChangeInfoBase,
): CallableReferenceFix.ConvertToLambda? {
    val oldReceiverInfo = changeInfo.oldReceiverInfo ?: return null
    val receiver = expression.receiverExpression ?: return null   // `::fn` with implicit receiver: no safe rewrite
    val varargOldName = changeInfo.methodDeclaration()?.varargName()

    val valueParams = changeInfo.newParameters
        .filter { !it.isContextParameter && it != oldReceiverInfo }
        .sortedBy { it.oldIndex }

    val (lambdaParams, contextValue) = if (isUnboundTypeQualifier(expression)) {
        // old shape (Recv, X1..Xn) -> R: the receiver becomes the first lambda parameter
        val receiverName = oldReceiverInfo.name
        listOf(receiverName) + valueParams.map { it.name } to receiverName
    } else {
        // bound `c::fn`, old shape (X1..Xn) -> R: the qualifier value moves into context(...).
        // `c::fn` evaluates `c` once; the lambda would re-evaluate it per invocation,
        // so only qualifiers without side effects are safe.
        if (receiver !is KtSimpleNameExpression && receiver !is KtThisExpression) return null
        valueParams.map { it.name } to receiver.text
    }

    val callee = calleeName(expression, changeInfo)
    val call =
        if (changeInfo.methodDeclaration() is KtProperty) callee
        else "$callee(${valueParams.joinToString(", ") { renderArg(it, varargOldName) }})"
    return CallableReferenceFix.ConvertToLambda(lambdaText(lambdaParams, "context($contextValue) { $call }"))
}

private fun KotlinChangeInfoBase.methodDeclaration(): KtCallableDeclaration? =
    method.unwrapped as? KtCallableDeclaration

private fun KtCallableDeclaration.varargName(): String? =
    valueParameters.firstOrNull { it.isVarArg }?.name

/** `name`, or `*name` when the old param was a vararg (arrays in the function type are spread back). */
private fun renderArg(param: KotlinParameterInfo, varargOldName: String?): String =
    if (varargOldName != null && param.oldName == varargOldName) "*${param.name}" else param.name

/** Unqualified member access needs the implicit dispatch receiver -> usage must be inside the class. */
private fun usageInsideContainingClass(method: KtCallableDeclaration, expression: KtElement): Boolean {
    val containingClass = method.containingClassOrObject ?: return true
    return PsiTreeUtil.isAncestor(containingClass, expression, true)
}

/** True when the callable reference resolves without errors in the pre-change code. */
@OptIn(KaExperimentalApi::class)
context(session: KaSession)
private fun isResolvable(expression: KtCallableReferenceExpression): Boolean =
    expression.diagnostics().none { it.severity == KaSeverity.ERROR } &&
            expression.callableReference.resolveSuccessfulSymbol() != null

context(session: KaSession)
private fun isUsedAsFunctionType(expression: KtCallableReferenceExpression): Boolean {
    val expectedType = expression.expectedType as? KaFunctionType ?: return false
    return !expectedType.isReflectType && expectedType.receiverType == null
}

private fun isMemberCallable(changeInfo: KotlinChangeInfoBase): Boolean =
    (changeInfo.method.unwrapped as? KtCallableDeclaration)?.containingClassOrObject != null

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