// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.kotlin.internal

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.base.KaConstantValue
import org.jetbrains.kotlin.analysis.api.resolution.successfulVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.psiSafe
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtVariableDeclaration
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UUnaryExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UastPrefixOperator

internal object FirKotlinUastConstantEvaluator {
    fun evaluate(uExpression: UExpression): Any? {
        val ktExpression = uExpression.sourcePsi as? KtExpression ?: return null
        analyzeForUast(ktExpression) {
            val expressionToEvaluate = ktExpression.unwrapKotlinValPropertyReference()
            expressionToEvaluate?.evaluate()
                ?.takeUnless { it is KaConstantValue.ErrorValue }?.value
                ?.let { return it }
        }
        return try {
            evaluateConstLike(uExpression)
        } catch (_: ArithmeticException) {
            return null
        }
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun KtExpression.unwrapKotlinValPropertyReference(): KtExpression? {
        if (this !is KtNameReferenceExpression) return this
        val variableSymbol = resolveToCall()?.successfulVariableAccessCall()?.symbol ?: return this
        if (!variableSymbol.isVal) {
            // can't evaluate non-final variables
            return null
        }
        return (variableSymbol as? KaPropertySymbol)?.initializer?.initializerPsi
            ?: variableSymbol.psiSafe<KtVariableDeclaration>()?.initializer
    }

    private fun evaluateConstLike(uExpression: UExpression): Any? {
        // By reaching here, we assume that AA's regular [evaluate] doesn't return
        // any meaningful value because it's not really compile-time constant.
        // However, for some cases, we can still evaluate if operands are const-like,
        // e.g., local final variable with constant initializer.
        return when (uExpression) {
            is UUnaryExpression -> evaluateConstLike(uExpression)
            is UPolyadicExpression -> evaluateConstLike(uExpression)
            else -> null
        }
    }

    private fun evaluateConstLike(uUnaryExpression: UUnaryExpression): Any? {
        when (uUnaryExpression.operator) {
            UastPrefixOperator.UNARY_PLUS -> {
                return evaluate(uUnaryExpression.operand)
            }
            UastPrefixOperator.UNARY_MINUS -> {
                val opr = evaluate(uUnaryExpression.operand) as? Number ?: return null
                return when (opr) {
                    is Byte -> opr.unaryMinus()
                    is Short -> opr.unaryMinus()
                    is Int -> opr.unaryMinus()
                    is Long -> opr.unaryMinus()
                    is Float -> opr.unaryMinus()
                    is Double -> opr.unaryMinus()
                    else -> null
                }
            }
            UastPrefixOperator.INC -> {
                val opr = evaluate(uUnaryExpression.operand) as? Number ?: return null
                return when (opr) {
                    is Byte -> opr.inc()
                    is Short -> opr.inc()
                    is Int -> opr.inc()
                    is Long -> opr.inc()
                    is Float -> opr.inc()
                    is Double -> opr.inc()
                    else -> null
                }
            }
            UastPrefixOperator.DEC -> {
                val opr = evaluate(uUnaryExpression.operand) as? Number ?: return null
                return when (opr) {
                    is Byte -> opr.dec()
                    is Short -> opr.dec()
                    is Int -> opr.dec()
                    is Long -> opr.dec()
                    is Float -> opr.dec()
                    is Double -> opr.dec()
                    else -> null
                }
            }
        }
        return null
    }

    private fun evaluateConstLike(uPolyadicExpression: UPolyadicExpression): Any? {
        val operands = uPolyadicExpression.operands.map { opr ->
            // Either String concatenation or numeric operations
            evaluate(opr)?.takeIf { it is String || it is Number }
                // If any operand cannot be evaluated, bail out early.
                ?: return null
        }
        return when (val op = uPolyadicExpression.operator) {
            UastBinaryOperator.PLUS -> {
                reduce(op, operands, Byte::plus, Short::plus, Int::plus, Long::plus, Float::plus, Double::plus)
            }
            UastBinaryOperator.MINUS -> {
                reduce(op, operands, Byte::minus, Short::minus, Int::minus, Long::minus, Float::minus, Double::minus)
            }
            UastBinaryOperator.MULTIPLY-> {
                reduce(op, operands, Byte::times, Short::times, Int::times, Long::times, Float::times, Double::times)
            }
            UastBinaryOperator.DIV-> {
                reduce(op, operands, Byte::div, Short::div, Int::div, Long::div, Float::div, Double::div)
            }
            // TODO: mod?
            // TODO: bitwise operators?
            else -> null
        }
    }

    private fun reduce(
        operator: UastBinaryOperator,
        operands: Collection<Any>,
        opByte: (Byte, Byte) -> Int,
        opShort: (Short, Short) -> Int,
        opInt: (Int, Int) -> Int,
        opLong: (Long, Long) -> Long,
        opFloat: (Float, Float) -> Float,
        opDouble: (Double, Double) -> Double,
    ): Any? {
        // Either String concatenation or numeric operations
        if (operands.any { it is String } && operator != UastBinaryOperator.PLUS) {
            return null
        }
        return operands.asSequence().reduceOrNull { opr1, opr2 ->
            if (opr1 is String || opr2 is String) {
                return@reduceOrNull opr1.toString() + opr2.toString()
            }
            when (opr1) {
                is Byte -> {
                    opByte(opr1, opr2 as? Byte ?: return@reduce null)
                }
                is Short -> {
                    opShort(opr1, opr2 as? Short ?: return@reduce null)
                }
                is Int -> {
                    opInt(opr1, opr2 as? Int ?: return@reduce null)
                }
                is Long -> {
                    opLong(opr1, opr2 as? Long ?: return@reduce null)
                }
                is Float -> {
                    opFloat(opr1, opr2 as? Float ?: return@reduce null)
                }
                is Double -> {
                    opDouble(opr1, opr2 as? Double ?: return@reduce null)
                }
                else -> return@reduce null
           }
       }
    }
}