// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.util

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.signatures.KaVariableSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.idea.codeinsight.utils.TypeParameterUtils.collectTypeParametersOnWhichReturnTypeDepends
import org.jetbrains.kotlin.idea.codeinsight.utils.TypeParameterUtils.returnTypeOfCallDependsOnTypeParameters
import org.jetbrains.kotlin.idea.codeinsight.utils.singleReturnExpressionOrNull
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtVariableDeclaration
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.lastBlockStatementOrThis
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

context(KaSession)
fun areTypeArgumentsRedundant(element: KtTypeArgumentList): Boolean {
    val callExpression = element.parent as? KtCallExpression ?: return false
    val resolvedCall = callExpression.resolveToCall()?.singleFunctionCallOrNull() ?: return false

    val typesInferredFromArguments = collectTypesInferredFromArguments(resolvedCall) ?: return false
    val typesInferredFromExtensionReceiver = collectTypesInferredFromExtensionReceiver(resolvedCall)

    val uninferredTypes = resolvedCall.typeArgumentsMapping.keys - typesInferredFromArguments - typesInferredFromExtensionReceiver

    if (uninferredTypes.isEmpty()) return true

    val callExpressionType = callExpression.expressionType ?: return false
    val typesPotentiallyInferredFromContext = collectTypeParametersOnWhichReturnTypeDepends(callExpression)
    if ((uninferredTypes - typesPotentiallyInferredFromContext).isNotEmpty()) return false
    val expectedType = findExpectedType(callExpression)

    if (expectedType != null) {
        return isApplicableType(callExpressionType, expectedType)
    }

    val outerCallExpression = callExpression.getParentOfType<KtCallExpression>(true) ?: return false
    return canTypesInferredFromOuterCall(outerCallExpression, callExpression)
}


context(KaSession)
private fun collectTypesInferredFromArguments(resolvedCall: KaFunctionCall<*>): Set<KaTypeParameterSymbol>? {
    val result = mutableSetOf<KaTypeParameterSymbol>()
    for ((argumentExpression, sig) in resolvedCall.argumentMapping) {
        if (argumentExpression is KtLambdaExpression && argumentExpression.isExplicitTypeArgumentsNeededForTypeInference(sig)) {
            return null
        }
        val argumentType = argumentExpression.expressionType ?: continue
        if (!argumentType.isSubtypeOf(sig.returnType)) return null
        if (isApplicableType(argumentType, sig.returnType)) {
            val collectTypeParameterTypes = if (argumentExpression is KtCallExpression && argumentExpression.typeArgumentList == null) {
                val resolvedArgumentCall = argumentExpression.resolveToCall()?.singleFunctionCallOrNull() ?: continue
                val collectTypesInferredFromArguments = collectTypesInferredFromArguments(resolvedArgumentCall) ?: continue
                if (collectTypesInferredFromArguments.containsAll(resolvedArgumentCall.typeArgumentsMapping.keys)) {
                    collectTypeParameterTypes(sig.symbol.returnType).map { it.symbol }
                } else {
                    emptySet()
                }
            } else {
                collectTypeParameterTypes(sig.symbol.returnType).map { it.symbol }
            }
            collectTypeParameterTypes.let { result.addAll(it) }
        }
    }
    return result
}

context(KaSession)
private fun collectTypesInferredFromExtensionReceiver(resolvedCall: KaFunctionCall<*>): Set<KaTypeParameterSymbol> {
    val receiverParameterType = resolvedCall.partiallyAppliedSymbol.symbol.receiverParameter?.type ?: return emptySet()
    val extensionReceiverType = resolvedCall.partiallyAppliedSymbol.extensionReceiver?.type ?: return emptySet()
    val typeArgumentsMapping = resolvedCall.typeArgumentsMapping
    val collectedTypeParameterTypes = collectTypeParameterTypes(receiverParameterType).map { it.symbol }.toSet()
    if (collectedTypeParameterTypes.isEmpty()) return emptySet()

    val builtType = buildType(receiverParameterType, typeArgumentsMapping)
    return if (builtType == extensionReceiverType) {
        collectedTypeParameterTypes
    } else {
        emptySet()
    }
}

context(KaSession)
private fun buildType(type: KaType, typeArgumentsMapping: Map<KaTypeParameterSymbol, KaType>): KaType? {
    return when (type) {
        is KaTypeParameterType -> typeArgumentsMapping[type.symbol]

        is KaClassType -> buildClassType(type.symbol) {
            type.typeArguments.mapNotNull { it.type }.forEach {
                val builtType = buildType(it, typeArgumentsMapping)
                if (builtType != null) argument(builtType)
            }
        }

        else -> null
    }
}

context(KaSession)
private fun isApplicableType(type: KaType, expectedType: KaType): Boolean {
    return type.semanticallyEquals(expectedType) ||
            (expectedType.isMarkedNullable && type.withNullability(KaTypeNullability.NULLABLE).semanticallyEquals(expectedType))
}

context(KaSession)
private fun findExpectedType(callExpression: KtCallExpression): KaType? {
    for (element in callExpression.parentsWithSelf) {
        if (element !is KtExpression) continue

        when (val parent = element.parent) {
            is KtNamedFunction -> {
                if (element != parent.bodyExpression || !parent.hasDeclaredReturnType()) return null
                if (!parent.hasBlockBody()) return parent.returnType
                val returnedExpression = parent.singleReturnExpressionOrNull?.returnedExpression

                return if (returnedExpression != null && extractReturnExpressions(returnedExpression).contains(callExpression)) {
                    parent.returnType
                } else {
                    null
                }
            }

            is KtVariableDeclaration -> {
                return if (element == parent.initializer && parent.typeReference != null) parent.returnType else null
            }

            is KtParameter -> {
                return if (element == parent.defaultValue) parent.returnType else null
            }

            is KtPropertyAccessor -> {
                val property = parent.parent as KtProperty
                return when {
                    element != parent.bodyExpression || parent.hasBlockBody() -> null
                    property.typeReference == null -> null
                    else -> parent.returnType
                }
            }

            is KtLambdaArgument -> {}

            is KtValueArgument -> {
                val parentCallExpression = parent.getParentOfType<KtCallExpression>(true) ?: return null
                val resolvedParentCall = parentCallExpression.resolveToCall()?.singleFunctionCallOrNull() ?: return null
                val parameter = resolvedParentCall.argumentMapping[callExpression] ?: return null
                val parameterType = parameter.symbol.returnType
                return if (collectTypeParameterTypes(parameterType).isEmpty()) {
                    parameterType
                } else {
                    null
                }
            }
        }

    }
    return null
}

private fun extractReturnExpressions(expression: KtExpression): Set<KtExpression> {
    return when (expression) {
        is KtIfExpression -> setOfNotNull(
            expression.then?.lastBlockStatementOrThis(), expression.`else`?.lastBlockStatementOrThis()
        )

        else -> setOf(expression)
    }
}

context(KaSession)
private fun canTypesInferredFromOuterCall(outerCallExpression: KtCallExpression, argumentExpression: KtCallExpression): Boolean {
    val argumentType = argumentExpression.expressionType ?: return false
    val resolvedCall = outerCallExpression.resolveToCall()?.singleFunctionCallOrNull() ?: return false
    val argumentMapping = resolvedCall.argumentMapping
    if (!argumentMapping.contains(argumentExpression)) return false
    val parameterType = argumentMapping[argumentExpression]?.symbol?.returnType ?: return false
    val outerCallTypeArgumentsMapping = resolvedCall.typeArgumentsMapping
    if (outerCallExpression.typeArgumentList != null ||
        (returnTypeOfCallDependsOnTypeParameters(outerCallExpression) && findExpectedType(outerCallExpression) != null)
    ) {
        val builtType = buildType(parameterType, outerCallTypeArgumentsMapping)
        if (builtType != null) {
            return isApplicableType(argumentType, builtType)
        }
    }

    return canTypesInferredFromAnotherArguments(argumentExpression, resolvedCall)
}

context(KaSession)
private fun canTypesInferredFromAnotherArguments(
    argumentExpression: KtCallExpression,
    resolvedCall: KaFunctionCall<*>,
): Boolean {
    val argumentMapping = resolvedCall.argumentMapping
    val typeArgumentsMapping = resolvedCall.typeArgumentsMapping
    val parameter = argumentMapping[argumentExpression] ?: return false
    val collectTypeParameterTypes = collectTypeParameterTypes(parameter.symbol.returnType).toMutableSet()

    for ((anotherArgumentExpression, sig) in argumentMapping) {
        if (anotherArgumentExpression == argumentExpression) continue
        val builtType = buildType(sig.symbol.returnType, typeArgumentsMapping)
        val anotherArgumentType = anotherArgumentExpression.expressionType ?: continue
        if (builtType == null || !isApplicableType(anotherArgumentType, builtType)) continue
        if (anotherArgumentExpression !is KtCallExpression || anotherArgumentExpression.typeArgumentList != null) {
            collectTypeParameterTypes.removeAll(collectTypeParameterTypes(sig.symbol.returnType))
        } else {
            val anotherResolvedCall = anotherArgumentExpression.resolveToCall()?.singleFunctionCallOrNull() ?: continue
            val typeParametersInferredFromArguments = collectTypesInferredFromArguments(anotherResolvedCall) ?: continue
            val typeParametersOnWhichReturnTypeDepends = collectTypeParametersOnWhichReturnTypeDepends(anotherArgumentExpression)
            if (typeParametersInferredFromArguments.containsAll(typeParametersOnWhichReturnTypeDepends)) {
                collectTypeParameterTypes.removeAll(collectTypeParameterTypes(sig.symbol.returnType))
            }
        }
    }
    return collectTypeParameterTypes.isEmpty()
}

context(KaSession)
private fun collectTypeParameterTypes(type: KaType): Set<KaTypeParameterType> {
    val result = mutableSetOf<KaTypeParameterType>()

    fun collect(type: KaType) {
        when (type) {
            is KaTypeParameterType -> result.add(type)
            is KaClassType -> type.typeArguments.mapNotNull { it.type }.forEach { collect(it) }
            else -> {}
        }
    }

    collect(type)
    return result
}

/**
 * See [org.jetbrains.kotlin.idea.k2.intentions.tests.K2IntentionTestGenerated.RemoveExplicitTypeArguments.testInapplicableTypeThatIsAFunItCannotBeInferred]
 */
context(KaSession)
private fun KtLambdaExpression.isExplicitTypeArgumentsNeededForTypeInference(
    parameter: KaVariableSignature<KaValueParameterSymbol>
): Boolean {
    if (!parameter.returnType.isFunctionType) return false
    val parameterType = parameter.symbol.returnType
    if (collectTypeParameterTypes(parameterType).isEmpty()) return false
    return valueParameters.isEmpty() || valueParameters.any { it.typeReference == null }
}

