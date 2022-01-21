// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.conversions.PrimitiveTypeCastsConversion.Companion.castToAsPrimitiveTypes
import org.jetbrains.kotlin.nj2k.isEquals
import org.jetbrains.kotlin.nj2k.parenthesizeIfBinaryExpression
import org.jetbrains.kotlin.nj2k.symbols.JKMethodSymbol
import org.jetbrains.kotlin.nj2k.symbols.isUnresolved
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.tree.JKOperatorToken.Companion.ARITHMETIC_OPERATORS
import org.jetbrains.kotlin.nj2k.tree.JKOperatorToken.Companion.BITWISE_LOGICAL_OPERATORS
import org.jetbrains.kotlin.nj2k.tree.JKOperatorToken.Companion.SHIFT_OPERATORS
import org.jetbrains.kotlin.nj2k.types.*

import org.jetbrains.kotlin.resolve.jvm.JvmPrimitiveType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class ImplicitCastsConversion(context: NewJ2kConverterContext) : RecursiveApplicableConversionBase(context) {
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        when (element) {
            is JKVariable -> convertVariable(element)
            is JKCallExpression -> convertMethodCallExpression(element)
            is JKBinaryExpression -> return recurse(convertBinaryExpression(element))
            is JKKtAssignmentStatement -> convertAssignmentStatement(element)
        }
        return recurse(element)
    }

    private fun convertBinaryExpression(binaryExpression: JKBinaryExpression): JKExpression {
        fun JKBinaryExpression.convert(): JKBinaryExpression {
            val leftType = left.calculateType(typeFactory)?.asPrimitiveType() ?: return this
            val rightType = right.calculateType(typeFactory)?.asPrimitiveType() ?: return this
            val leftOperandCasted by lazy(LazyThreadSafetyMode.NONE) {
                JKBinaryExpression(
                    ::left.detached().let { it.castTo(rightType, strict = true) ?: it },
                    ::right.detached(),
                    operator
                ).withFormattingFrom(this)
            }
            val rightOperandCasted by lazy(LazyThreadSafetyMode.NONE) {
                JKBinaryExpression(
                    ::left.detached(),
                    ::right.detached().let { it.castTo(leftType, strict = true) ?: it },
                    operator
                ).withFormattingFrom(this)
            }

            return when {
                leftType.isBoolean() || rightType.isBoolean() -> this

                operator.token in SHIFT_OPERATORS -> {
                    val newLeftType = if (leftType.isLong()) JKJavaPrimitiveType.LONG else JKJavaPrimitiveType.INT
                    JKBinaryExpression(
                        ::left.detached().let { it.castTo(newLeftType, strict = true) ?: it },
                        ::right.detached().let { it.castTo(JKJavaPrimitiveType.INT, strict = true) ?: it },
                        operator
                    ).withFormattingFrom(this)
                }

                operator.token in BITWISE_LOGICAL_OPERATORS -> {
                    val commonSupertype = if (leftType.isLong() || rightType.isLong()) {
                        JKJavaPrimitiveType.LONG
                    } else {
                        JKJavaPrimitiveType.INT
                    }
                    JKBinaryExpression(
                        ::left.detached().let { it.castTo(commonSupertype, strict = true) ?: it },
                        ::right.detached().let { it.castTo(commonSupertype, strict = true) ?: it },
                        operator
                    ).withFormattingFrom(this)
                }

                leftType.isChar() && rightType.isChar() && operator.token in ARITHMETIC_OPERATORS -> {
                    JKBinaryExpression(
                        ::left.detached().let { it.castTo(JKJavaPrimitiveType.INT, strict = true) ?: it },
                        ::right.detached().let { it.castTo(JKJavaPrimitiveType.INT, strict = true) ?: it },
                        operator
                    ).withFormattingFrom(this)
                }

                leftType.jvmPrimitiveType == rightType.jvmPrimitiveType -> this

                leftType.isChar() -> leftOperandCasted

                rightType.isChar() -> rightOperandCasted

                operator.isEquals() ->
                    if (rightType isStrongerThan leftType) leftOperandCasted
                    else rightOperandCasted

                else -> this
            }
        }

        return binaryExpression.convert()
    }

    private fun convertVariable(variable: JKVariable) {
        if (variable.initializer is JKStubExpression) return
        variable.initializer.castTo(variable.type.type)?.also {
            variable.initializer = it
        }
    }

    private fun convertAssignmentStatement(statement: JKKtAssignmentStatement) {
        val expressionType = statement.field.calculateType(typeFactory) ?: return
        statement.expression.castTo(expressionType)?.also {
            statement.expression = it
        }
    }

    private fun convertMethodCallExpression(expression: JKCallExpression) {
        if (expression.identifier.isUnresolved) return
        val parameterTypes = expression.identifier.parameterTypesWithLastArgumentUnfoldedAsVararg() ?: return
        val newArguments = expression.arguments.arguments.mapIndexed { argumentIndex, argument ->
            val toType = parameterTypes.getOrNull(argumentIndex) ?: parameterTypes.last()
            argument.value.castTo(toType)
        }
        val needUpdate = newArguments.any { it != null }
        if (needUpdate) {
            for ((newArgument, oldArgument) in newArguments zip expression.arguments.arguments) {
                if (newArgument != null) {
                    oldArgument.value = newArgument.copyTreeAndDetach()
                }
            }
        }
    }

    private fun JKExpression.castStringToRegex(toType: JKType): JKExpression? {
        if (toType.safeAs<JKClassType>()?.classReference?.fqName != "java.util.regex.Pattern") return null
        val expressionType = calculateType(typeFactory) ?: return null
        if (!expressionType.isStringType()) return null
        return JKQualifiedExpression(
            copyTreeAndDetach().parenthesizeIfBinaryExpression(),
            JKCallExpressionImpl(
                symbolProvider.provideMethodSymbol("kotlin.text.toRegex"),
                JKArgumentList(),
                JKTypeArgumentList()
            )
        )
    }

    private fun JKExpression.castTo(toType: JKType, strict: Boolean = false): JKExpression? {
        val expressionType = calculateType(typeFactory)
        if (expressionType == toType) return null
        castToAsPrimitiveTypes(this, toType, strict)?.also { return it }
        castStringToRegex(toType)?.also { return it }
        return null
    }

    private fun JKMethodSymbol.parameterTypesWithLastArgumentUnfoldedAsVararg(): List<JKType>? {
        val realParameterTypes = parameterTypes ?: return null
        if (realParameterTypes.isEmpty()) return null
        val lastArrayType = realParameterTypes.lastOrNull()?.arrayInnerType() ?: return realParameterTypes
        return realParameterTypes.subList(0, realParameterTypes.lastIndex) + lastArrayType
    }

    private fun JKJavaPrimitiveType.isBoolean() = jvmPrimitiveType == JvmPrimitiveType.BOOLEAN
    private fun JKJavaPrimitiveType.isChar() = jvmPrimitiveType == JvmPrimitiveType.CHAR
    private fun JKJavaPrimitiveType.isLong() = jvmPrimitiveType == JvmPrimitiveType.LONG
}
