// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.util

import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.elementType
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.k2.refactoring.moveFunctionLiteralOutsideParenthesesIfPossible
import org.jetbrains.kotlin.idea.refactoring.getLastLambdaExpression
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElementSelector
import org.jetbrains.kotlin.types.Variance

object ConvertReferenceToLambdaUtil {

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    fun prepareLambdaExpressionText(
        element: KtCallableReferenceExpression,
    ): String? {
        val valueArgumentParent = element.parent as? KtValueArgument
        val callGrandParent = valueArgumentParent?.parent?.parent as? KtCallExpression
        val resolvedCall = callGrandParent?.resolveToCall()?.successfulFunctionCallOrNull()
        val matchingParameterType = resolvedCall?.argumentMapping?.get(element)?.returnType
        val matchingParameterIsExtension = matchingParameterType is KaFunctionType && matchingParameterType.receiverType != null

        val receiverExpression = element.receiverExpression
        val receiverType = element.receiverType

        val symbol = element.callableReference.mainReference.resolveToSymbol() ?: return null

        val callableSymbol = (symbol as? KaCallableSymbol)?.let {
            (it as? KaValueParameterSymbol)?.generatedPrimaryConstructorProperty ?: it
        }

        val receiverSymbol = receiverExpression?.getQualifiedElementSelector()?.mainReference?.resolveToSymbol()
        val acceptsReceiverAsParameter = receiverSymbol is KaClassSymbol &&
                !matchingParameterIsExtension &&
                (callableSymbol as? KaNamedFunctionSymbol)?.isStatic != true && !receiverSymbol.classKind.isObject &&
                (callableSymbol?.containingDeclaration != null || callableSymbol?.isExtension == true || symbol is KaNamedClassSymbol && symbol.isInner)

        val parameterNamesAndTypes =
            if (callableSymbol is KaFunctionSymbol) {
                val paramNameAndTypes = callableSymbol.valueParameters.map { it.name.asString() to it.returnType }
                if (matchingParameterType != null) {
                    val parameterSize = (matchingParameterType.lowerBoundIfFlexible() as KaClassType).typeArguments.size - (if (acceptsReceiverAsParameter) 2 else 1)
                    if (parameterSize >= 0) paramNameAndTypes.take(parameterSize) else paramNameAndTypes
                } else {
                    paramNameAndTypes
                }
            } else {
                emptyList()
            }

        val receiverNameAndType = if (receiverType != null) {
            KotlinNameSuggester().suggestTypeNames(receiverType).map {
                KotlinNameSuggester.suggestNameByName(it) { name ->
                    name !in parameterNamesAndTypes.map { pair -> pair.first }
                }
            }.first() to receiverType
        } else {
            null
        }

        val psiFactory = KtPsiFactory(element.project)
        val targetName = element.callableReference.getReferencedName()
        val lambdaParameterNamesAndTypes = if (acceptsReceiverAsParameter)
            listOf(receiverNameAndType!!) + parameterNamesAndTypes
        else
            parameterNamesAndTypes

        val receiverPrefix = when {
            acceptsReceiverAsParameter -> receiverNameAndType!!.first + "."
            matchingParameterIsExtension -> ""
            else -> receiverExpression?.let { it.text + "." } ?: ""
        }

        val lambdaExpression = if (valueArgumentParent != null &&
            lambdaParameterNamesAndTypes.size == 1 &&
            receiverExpression?.text != StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier
        ) {
            val body = if (acceptsReceiverAsParameter) {
                if (callableSymbol is KaPropertySymbol) "it.$targetName"
                else "it.$targetName()"
            } else {
                "$receiverPrefix$targetName(${if (matchingParameterIsExtension) "this" else "it"})"
            }

            psiFactory.createLambdaExpression(parameters = "", body = body)
        } else {
            val isExtension = matchingParameterIsExtension && resolvedCall.symbol.isExtension == true
            val (params, args) = if (isExtension) {
                val thisArgument = if (parameterNamesAndTypes.isNotEmpty()) listOf("this") else emptyList()
                lambdaParameterNamesAndTypes.drop(1) to (thisArgument + parameterNamesAndTypes.drop(1).map { it.first })
            } else {
                lambdaParameterNamesAndTypes to parameterNamesAndTypes.map { it.first }
            }

            psiFactory.createLambdaExpression(
                parameters = params.joinToString(separator = ", ") {
                    if (valueArgumentParent != null) it.first
                    else it.first + ": " + it.second.render(position = Variance.IN_VARIANCE)
                },
                body = if (callableSymbol is KaPropertySymbol) {
                    "$receiverPrefix$targetName"
                } else {
                    args.joinToString(prefix = "$receiverPrefix$targetName(", separator = ", ", postfix = ")")
                }
            )
        }

        val needParentheses = lambdaParameterNamesAndTypes.isEmpty() && when (element.parent.elementType) {
            KtNodeTypes.WHEN_ENTRY, KtNodeTypes.THEN, KtNodeTypes.ELSE -> true
            else -> false
        }

        val wrappedExpression = if (needParentheses)
            psiFactory.createExpressionByPattern("($0)", lambdaExpression)
        else
            lambdaExpression

        return wrappedExpression.text
    }

    fun convertReferenceToLambdaExpression(
        element: KtCallableReferenceExpression,
        lambdaExpressionText: String
    ): KtExpression? {
        val valueArgumentParent = element.parent as? KtValueArgument
        val callGrandParent = valueArgumentParent?.parent?.parent as? KtCallExpression
        val wrappedExpression = KtPsiFactory.contextual(element).createExpression(lambdaExpressionText)
        val lambdaExpression = element.replaced(wrappedExpression)
        val pointer = lambdaExpression.createSmartPointer()
        shortenReferences(lambdaExpression)
        if (callGrandParent == null) return pointer.element
        val lastLambdaExpression = callGrandParent.getLastLambdaExpression()
        lastLambdaExpression?.moveFunctionLiteralOutsideParenthesesIfPossible()
        return callGrandParent.lambdaArguments.lastOrNull()?.getArgumentExpression() ?: lastLambdaExpression ?: pointer.element
    }
}