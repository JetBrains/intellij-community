// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import com.intellij.psi.PsiNewExpression
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.nj2k.*
import org.jetbrains.kotlin.nj2k.conversions.PrimitiveTypeCastsConversion.Companion.castToAsPrimitiveTypes
import org.jetbrains.kotlin.nj2k.symbols.JKMethodSymbol
import org.jetbrains.kotlin.nj2k.symbols.isUnresolved
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.tree.JKOperatorToken.Companion.ARITHMETIC_OPERATORS
import org.jetbrains.kotlin.nj2k.tree.JKOperatorToken.Companion.BITWISE_LOGICAL_OPERATORS
import org.jetbrains.kotlin.nj2k.tree.JKOperatorToken.Companion.RANGE_OPERATORS
import org.jetbrains.kotlin.nj2k.tree.JKOperatorToken.Companion.SHIFT_OPERATORS
import org.jetbrains.kotlin.nj2k.types.*
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class ImplicitCastsConversion(context: ConverterContext) : RecursiveConversion(context) {
    context(_: KaSession)
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        when (element) {
            is JKVariable -> convertVariable(element)
            is JKCallExpression -> convertMethodCallExpression(element)
            is JKNewExpression -> convertNewExpression(element)
            is JKBinaryExpression -> return recurse(element.convert())
            is JKIfElseExpression -> convertIfElseExpression(element)
            is JKKtAssignmentStatement -> convertAssignmentStatement(element)
            is JKArrayAccessExpression -> convertArrayAccessExpression(element)
            is JKReturnStatement -> convertReturnStatement(element)
        }
        return recurse(element)
    }

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
                if (rightType isStrongerThan leftType) leftOperandCasted else rightOperandCasted

            operator.token in RANGE_OPERATORS && rightType.isFloatingPoint() -> {
                // A special case when the return type of the right part of a range was changed in BuiltinMembersConversion
                // (for example, Java's `Math.max` returns `int`, but Kotlin's `max` returns `Double`)
                rightOperandCasted
            }

            else -> this
        }
    }

    private fun convertVariable(variable: JKVariable) {
        if (variable.initializer is JKStubExpression) return
        variable.initializer.castTo(variable.type.type)?.also {
            variable.initializer = it
        }
    }

    private fun convertAssignmentStatement(statement: JKKtAssignmentStatement) {
        val isCompoundAssignment = compoundAssignmentMap.contains(statement.token)

        if (isCompoundAssignment) {
            convertCompoundAssignment(statement)
        } else {
            // regular assignment
            val fieldType = statement.field.calculateType(typeFactory) ?: return
            statement.expression.castTo(fieldType)?.let {
                statement.expression = it
            }
        }
    }

    private fun convertCompoundAssignment(statement: JKKtAssignmentStatement) {
        val fieldType = statement.field.calculateType(typeFactory) ?: return
        val expressionType = statement.expression.calculateType(typeFactory) ?: return

        val fieldIsByte = fieldType.asPrimitiveType()?.isByte() == true
        val fieldIsShort = fieldType.asPrimitiveType()?.isShort() == true
        val isOnlyExpressionFloatingPointType =
            expressionType.asPrimitiveType()?.isFloatingPoint() == true &&
                    fieldType.asPrimitiveType()?.isFloatingPoint() == false

        when {
            fieldIsByte || fieldIsShort || isOnlyExpressionFloatingPointType -> {
                // Case 1: Byte and Short don't work with compound assignment (KT-7907)
                // Case 2: Code like `int *= double` loses the floating-point part of `double`
                // Both cases need to be converted to regular assignment
                val newToken = compoundAssignmentMap.getValue(statement.token)
                val newType = if (numberTypesStrongerThanInt.contains(expressionType.asPrimitiveType())) {
                    expressionType
                } else {
                    typeFactory.types.int
                }
                val newExpression = JKBinaryExpression(
                    left = statement.field.copyTreeAndDetach(),
                    right = statement.expression.copyTreeAndDetach().parenthesizeIfCompoundExpression(),
                    operator = JKKtOperatorImpl(newToken, newType)
                ).parenthesize()

                newExpression.castTo(fieldType)?.let {
                    statement.token = JKOperatorToken.EQ
                    statement.expression = it
                }
            }

            fieldType.asPrimitiveType()?.isChar() == true -> {
                val newExpression = if (expressionType.asPrimitiveType()?.isChar() == true) {
                    statement.expression.castTo(typeFactory.types.int)
                } else {
                    statement.expression.castTo(fieldType)?.castTo(typeFactory.types.int)
                }

                if (newExpression != null) {
                    statement.expression = newExpression
                }
            }

            else -> {
                statement.expression.castTo(fieldType)?.let {
                    statement.expression = it
                }
            }
        }
    }

    context(_: KaSession)
    private fun convertNewExpression(expression: JKNewExpression) {
        val constructor = expression.psi.safeAs<PsiNewExpression>()?.resolveConstructor() ?: return
        val methodSymbol = context.symbolProvider.provideDirectSymbol(constructor) as? JKMethodSymbol ?: return
        convertArguments(methodSymbol, expression.arguments.arguments)
    }

    context(_: KaSession)
    private fun convertMethodCallExpression(expression: JKCallExpression) {
        convertArguments(expression.identifier, expression.arguments.arguments)
    }

    private fun convertIfElseExpression(expression: JKIfElseExpression) {
        val type = expression.calculateType(typeFactory)?.asPrimitiveType() ?: return
        val thenType = expression.thenBranch.calculateType(typeFactory)?.asPrimitiveType() ?: return
        val elseType = expression.elseBranch.calculateType(typeFactory)?.asPrimitiveType() ?: return

        if (thenType != type) {
            expression.thenBranch.castTo(type)?.let {
                expression.thenBranch = it.copyTreeAndDetach()
            }
        }

        if (elseType != type) {
            expression.elseBranch.castTo(type)?.let {
                expression.elseBranch = it.copyTreeAndDetach()
            }
        }
    }

    private fun convertArrayAccessExpression(element: JKArrayAccessExpression) {
        element.indexExpression.castTo(typeFactory.types.int)?.let {
            element.indexExpression = it
        }
    }

    private fun convertReturnStatement(element: JKReturnStatement) {
        val method = element.parentOfType<JKMethod>() ?: return
        val expectedType = method.returnType.type.asPrimitiveType() ?: return
        element.expression.castTo(expectedType)?.let {
            element.expression = it
        }
    }

    context(_: KaSession)
    private fun convertArguments(methodSymbol: JKMethodSymbol, arguments: List<JKArgument>) {
        if (methodSymbol.isUnresolved) return
        val parameterTypes = methodSymbol.parameterTypesWithLastArgumentUnfoldedAsVararg() ?: return
        val newArguments = arguments.mapIndexed { argumentIndex, argument ->
            val toType = parameterTypes.getOrNull(argumentIndex) ?: parameterTypes.last()
            argument.value.castTo(toType)
        }
        val needUpdate = newArguments.any { it != null }
        if (needUpdate) {
            for ((newArgument, oldArgument) in newArguments zip arguments) {
                if (newArgument != null) {
                    oldArgument.value = newArgument.copyTreeAndDetach()
                }
            }
        }
    }

    private fun JKExpression.castTo(toType: JKType, strict: Boolean = false): JKExpression? {
        val expressionType = calculateType(typeFactory)
        if (expressionType == toType) return null
        castToAsPrimitiveTypes(this, toType, strict)?.also { return it }
        return null
    }

    context(_: KaSession)
    private fun JKMethodSymbol.parameterTypesWithLastArgumentUnfoldedAsVararg(): List<JKType>? {
        val realParameterTypes = parameterTypes ?: return null
        if (realParameterTypes.isEmpty()) return null
        val lastArrayType = realParameterTypes.lastOrNull()?.arrayInnerType() ?: return realParameterTypes
        return realParameterTypes.subList(0, realParameterTypes.lastIndex) + lastArrayType
    }
}

private val compoundAssignmentMap: Map<JKOperatorToken, JKOperatorToken> = mapOf(
    JKOperatorToken.PLUSEQ to JKOperatorToken.PLUS,
    JKOperatorToken.MINUSEQ to JKOperatorToken.MINUS,
    JKOperatorToken.MULTEQ to JKOperatorToken.MUL,
    JKOperatorToken.DIVEQ to JKOperatorToken.DIV,
    JKOperatorToken.PERCEQ to JKOperatorToken.PERC
)

private val numberTypesStrongerThanInt: Set<JKJavaPrimitiveType> = setOf(
    JKJavaPrimitiveType.LONG,
    JKJavaPrimitiveType.FLOAT,
    JKJavaPrimitiveType.DOUBLE,
)