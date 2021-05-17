// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.uast.*

class KotlinUBinaryExpression(
        override val sourcePsi: KtBinaryExpression,
        givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UBinaryExpression, KotlinUElementWithType, KotlinEvaluatableUElement {
    private companion object {
        val BITWISE_OPERATORS = mapOf(
                "or" to UastBinaryOperator.BITWISE_OR,
                "and" to UastBinaryOperator.BITWISE_AND,
                "xor" to UastBinaryOperator.BITWISE_XOR,
                "shl" to UastBinaryOperator.SHIFT_LEFT,
                "shr" to UastBinaryOperator.SHIFT_RIGHT,
                "ushr" to UastBinaryOperator.UNSIGNED_SHIFT_RIGHT
        )
    }

    override val leftOperand by lz { KotlinConverter.convertOrEmpty(sourcePsi.left, this) }
    override val rightOperand by lz { KotlinConverter.convertOrEmpty(sourcePsi.right, this) }

    override val operatorIdentifier: UIdentifier?
        get() = KotlinUIdentifier(sourcePsi.operationReference.getReferencedNameElement(), this)

    override fun resolveOperator(): PsiMethod? = resolveToPsiMethod(sourcePsi)

    override val operator = when (sourcePsi.operationToken) {
        KtTokens.EQ -> UastBinaryOperator.ASSIGN
        KtTokens.PLUS -> UastBinaryOperator.PLUS
        KtTokens.MINUS -> UastBinaryOperator.MINUS
        KtTokens.MUL -> UastBinaryOperator.MULTIPLY
        KtTokens.DIV -> UastBinaryOperator.DIV
        KtTokens.PERC -> UastBinaryOperator.MOD
        KtTokens.OROR -> UastBinaryOperator.LOGICAL_OR
        KtTokens.ANDAND -> UastBinaryOperator.LOGICAL_AND
        KtTokens.EQEQ -> UastBinaryOperator.EQUALS
        KtTokens.EXCLEQ -> UastBinaryOperator.NOT_EQUALS
        KtTokens.EQEQEQ -> UastBinaryOperator.IDENTITY_EQUALS
        KtTokens.EXCLEQEQEQ -> UastBinaryOperator.IDENTITY_NOT_EQUALS
        KtTokens.GT -> UastBinaryOperator.GREATER
        KtTokens.GTEQ -> UastBinaryOperator.GREATER_OR_EQUALS
        KtTokens.LT -> UastBinaryOperator.LESS
        KtTokens.LTEQ -> UastBinaryOperator.LESS_OR_EQUALS
        KtTokens.PLUSEQ -> UastBinaryOperator.PLUS_ASSIGN
        KtTokens.MINUSEQ -> UastBinaryOperator.MINUS_ASSIGN
        KtTokens.MULTEQ -> UastBinaryOperator.MULTIPLY_ASSIGN
        KtTokens.DIVEQ -> UastBinaryOperator.DIVIDE_ASSIGN
        KtTokens.PERCEQ -> UastBinaryOperator.REMAINDER_ASSIGN
        KtTokens.IN_KEYWORD -> KotlinBinaryOperators.IN
        KtTokens.NOT_IN -> KotlinBinaryOperators.NOT_IN
        KtTokens.RANGE -> KotlinBinaryOperators.RANGE_TO
        else -> run { // Handle bitwise operators
            val other = UastBinaryOperator.OTHER
            val ref = sourcePsi.operationReference
            val resolvedCall = sourcePsi.operationReference.getResolvedCall(ref.analyze()) ?: return@run other
            val resultingDescriptor = resolvedCall.resultingDescriptor as? FunctionDescriptor ?: return@run other
            val applicableOperator = BITWISE_OPERATORS[resultingDescriptor.name.asString()] ?: return@run other

            val containingClass = resultingDescriptor.containingDeclaration as? ClassDescriptor ?: return@run other
            if (containingClass.typeConstructor.supertypes.any {
                it.constructor.declarationDescriptor?.fqNameSafe?.asString() == "kotlin.Number"
            }) applicableOperator else other
        }
    }
}

class KotlinCustomUBinaryExpression(
        override val psi: PsiElement,
        givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UBinaryExpression {
    lateinit override var leftOperand: UExpression
        internal set

    lateinit override var operator: UastBinaryOperator
        internal set

    lateinit override var rightOperand: UExpression
        internal set

    override val operatorIdentifier: UIdentifier?
        get() = null

    override fun resolveOperator() = null
}
