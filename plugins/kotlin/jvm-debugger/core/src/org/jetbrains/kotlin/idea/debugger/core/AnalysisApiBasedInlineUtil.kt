// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.core

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.KtFunctionCall
import org.jetbrains.kotlin.analysis.api.calls.KtSuccessCallInfo
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KtConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*

object AnalysisApiBasedInlineUtil {
    fun KtAnalysisSession.isInlinedArgument(argument: KtFunction, checkNonLocalReturn: Boolean): Boolean {
        val argumentSymbol = getInlineArgumentSymbol(argument) ?: return false
        val argumentType = argumentSymbol.returnType
        return !argumentSymbol.isNoinline && isNotNullableFunctionType(argumentType) &&
                (!checkNonLocalReturn || !argumentSymbol.isCrossinline)
    }

    private fun KtAnalysisSession.getInlineArgumentSymbol(argument: KtFunction): KtValueParameterSymbol? {
        if (argument !is KtFunctionLiteral && argument !is KtNamedFunction) return null

        val parentCall = KtPsiUtil.getParentCallIfPresent(argument) as? KtCallExpression ?: return null
        val call = getResolvedFunctionCall(parentCall) ?: return null
        val callSymbol = call.partiallyAppliedSymbol.symbol

        if (!callSymbol.isInline() && !callSymbol.isArrayConstructorWithLambda()) return null

        val valueArgument = parentCall.getValueArgumentForExpression(argument) ?: return null
        return call.argumentMapping[valueArgument.getArgumentExpression()]?.symbol
    }

    fun KtAnalysisSession.getResolvedFunctionCall(callExpression: KtCallExpression): KtFunctionCall<*>? {
        val callInfo = callExpression.resolveCall() as? KtSuccessCallInfo ?: return null
        return callInfo.call as? KtFunctionCall<*>
    }

    private fun KtFunctionLikeSymbol.isInline(): Boolean =
        this is KtFunctionSymbol && isInline

    private fun KtAnalysisSession.isNotNullableFunctionType(type: KtType): Boolean =
        (type.isFunctionType || type.isSuspendFunctionType) && !type.isMarkedNullable

    private fun KtFunctionLikeSymbol.isArrayConstructorWithLambda(): Boolean =
        this is KtConstructorSymbol && valueParameters.size == 2 && returnType.isArrayOrPrimitiveArray()

    private fun KtType.isArrayOrPrimitiveArray(): Boolean =
        isClassTypeWithClassId(StandardClassIds.Array) ||
        StandardClassIds.elementTypeByPrimitiveArrayType.keys.any { isClassTypeWithClassId(it) }

    private fun KtType.isClassTypeWithClassId(classId: ClassId): Boolean =
        this is KtNonErrorClassType && this.classId == classId

    // Copied from org.jetbrains.kotlin.resolve.calls.util.callUtil.kt
    fun KtCallExpression.getValueArgumentForExpression(expression: KtExpression): ValueArgument? {
        fun KtElement.deparenthesizeStructurally(): KtElement? {
            val deparenthesized = if (this is KtExpression) KtPsiUtil.deparenthesizeOnce(this) else this
            return when {
                deparenthesized != this -> deparenthesized
                this is KtLambdaExpression -> this.functionLiteral
                this is KtFunctionLiteral -> this.bodyExpression
                else -> null
            }
        }

        fun KtElement.isParenthesizedExpression() = generateSequence(this) { it.deparenthesizeStructurally() }.any { it == expression }
        return valueArguments.firstOrNull { it?.getArgumentExpression()?.isParenthesizedExpression() ?: false }
    }
}
