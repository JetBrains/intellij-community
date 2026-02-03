// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import com.intellij.psi.PsiContinueStatement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiForStatement
import com.intellij.psi.PsiLabeledStatement
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiLoopStatement
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiStatement
import com.intellij.psi.PsiTypes
import com.intellij.util.IncorrectOperationException
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.j2k.ReferenceSearcher
import org.jetbrains.kotlin.j2k.hasWriteAccesses
import org.jetbrains.kotlin.lexer.KtTokens.EXCLEQ
import org.jetbrains.kotlin.lexer.KtTokens.GT
import org.jetbrains.kotlin.lexer.KtTokens.GTEQ
import org.jetbrains.kotlin.lexer.KtTokens.LT
import org.jetbrains.kotlin.lexer.KtTokens.LTEQ
import org.jetbrains.kotlin.nj2k.JKSymbolProvider
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.blockStatement
import org.jetbrains.kotlin.nj2k.downToExpression
import org.jetbrains.kotlin.nj2k.forEachDescendantOfType
import org.jetbrains.kotlin.nj2k.hasAnnotations
import org.jetbrains.kotlin.nj2k.parenthesizeIfCompoundExpression
import org.jetbrains.kotlin.nj2k.psi
import org.jetbrains.kotlin.nj2k.runExpression
import org.jetbrains.kotlin.nj2k.statements
import org.jetbrains.kotlin.nj2k.symbols.deepestFqName
import org.jetbrains.kotlin.nj2k.tree.JKArgumentList
import org.jetbrains.kotlin.nj2k.tree.JKBinaryExpression
import org.jetbrains.kotlin.nj2k.tree.JKBlock
import org.jetbrains.kotlin.nj2k.tree.JKBlockStatement
import org.jetbrains.kotlin.nj2k.tree.JKBlockStatementWithoutBrackets
import org.jetbrains.kotlin.nj2k.tree.JKCallExpression
import org.jetbrains.kotlin.nj2k.tree.JKCallExpressionImpl
import org.jetbrains.kotlin.nj2k.tree.JKContinueStatement
import org.jetbrains.kotlin.nj2k.tree.JKDeclarationStatement
import org.jetbrains.kotlin.nj2k.tree.JKEmptyStatement
import org.jetbrains.kotlin.nj2k.tree.JKExpression
import org.jetbrains.kotlin.nj2k.tree.JKExpressionStatement
import org.jetbrains.kotlin.nj2k.tree.JKFieldAccessExpression
import org.jetbrains.kotlin.nj2k.tree.JKForInStatement
import org.jetbrains.kotlin.nj2k.tree.JKForLoopParameter
import org.jetbrains.kotlin.nj2k.tree.JKJavaForLoopStatement
import org.jetbrains.kotlin.nj2k.tree.JKKtConvertedFromForLoopSyntheticWhileStatement
import org.jetbrains.kotlin.nj2k.tree.JKKtOperatorImpl
import org.jetbrains.kotlin.nj2k.tree.JKKtSingleValueOperatorToken
import org.jetbrains.kotlin.nj2k.tree.JKLabeledExpression
import org.jetbrains.kotlin.nj2k.tree.JKLiteralExpression
import org.jetbrains.kotlin.nj2k.tree.JKLocalVariable
import org.jetbrains.kotlin.nj2k.tree.JKOperator
import org.jetbrains.kotlin.nj2k.tree.JKOperatorToken
import org.jetbrains.kotlin.nj2k.tree.JKPostfixExpression
import org.jetbrains.kotlin.nj2k.tree.JKPrefixExpression
import org.jetbrains.kotlin.nj2k.tree.JKQualifiedExpression
import org.jetbrains.kotlin.nj2k.tree.JKStatement
import org.jetbrains.kotlin.nj2k.tree.JKStubExpression
import org.jetbrains.kotlin.nj2k.tree.JKTreeElement
import org.jetbrains.kotlin.nj2k.tree.JKTypeElement
import org.jetbrains.kotlin.nj2k.tree.JKVariable
import org.jetbrains.kotlin.nj2k.tree.JKWhileStatement
import org.jetbrains.kotlin.nj2k.tree.copyTreeAndDetach
import org.jetbrains.kotlin.nj2k.tree.detached
import org.jetbrains.kotlin.nj2k.tree.withFormattingFrom
import org.jetbrains.kotlin.nj2k.types.JKJavaArrayType
import org.jetbrains.kotlin.nj2k.types.JKJavaPrimitiveType
import org.jetbrains.kotlin.nj2k.types.JKNoType
import org.jetbrains.kotlin.nj2k.types.JKTypeFactory
import org.jetbrains.kotlin.nj2k.untilToExpression
import org.jetbrains.kotlin.utils.NumberWithRadix
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.kotlin.utils.extractRadix
import kotlin.math.abs

class ForConversion(context: ConverterContext) : RecursiveConversion(context) {
    private val forToWhile = ForToWhileConverter(context, symbolProvider)
    private val forToForeach = ForToForeachConverter(context, symbolProvider, typeFactory)

    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKJavaForLoopStatement) return recurse(element)
        val resultLoop = forToForeach.convert(element) ?: forToWhile.convert(element)
        return recurse(resultLoop.withFormattingFrom(element))
    }
}

private class ForToForeachConverter(
    private val context: ConverterContext,
    private val symbolProvider: JKSymbolProvider,
    private val typeFactory: JKTypeFactory
) {
    private val referenceSearcher: ReferenceSearcher
        get() = context.converter.referenceSearcher

    fun convert(loop: JKJavaForLoopStatement): JKForInStatement? {
        val initializer = loop.initializers.singleOrNull() ?: return null
        val loopVar = (initializer as? JKDeclarationStatement)?.declaredStatements?.singleOrNull() as? JKLocalVariable ?: return null
        val condition = loop.condition as? JKBinaryExpression ?: return null

        val left = condition.left as? JKFieldAccessExpression ?: return null
        if (left.identifier.target != loopVar) return null

        val rightType = condition.right.psi<PsiExpression>()?.type
        if (rightType in listOf(PsiTypes.doubleType(), PsiTypes.floatType(), PsiTypes.charType())) return null

        val operator = (loop.updaters.singleOrNull() as? JKExpressionStatement)?.expression?.incrementOrDecrementOperator(loopVar)
            ?: return null
        val isReversed = when (operator.token.text) {
            "++" -> false
            "--" -> true
            else -> return null
        }

        val conditionToken = condition.operator.safeAs<JKKtOperatorImpl>()?.token.safeAs<JKKtSingleValueOperatorToken>()?.psiToken
        val isInclusive = when (conditionToken) {
            LT -> if (isReversed) return null else false
            LTEQ -> if (isReversed) return null else true
            GT -> if (isReversed) false else return null
            GTEQ -> if (isReversed) true else return null
            EXCLEQ -> false
            else -> return null
        }

        val loopVarPsi = loopVar.psi<PsiLocalVariable>() ?: return null
        val loopBodyPsi = loop.body.psi<PsiElement>()
        val loopConditionPsi = loop.condition.psi<PsiElement>()

        val loopVariableIsMutated = loopVarPsi.hasWriteAccesses(referenceSearcher, loopBodyPsi) ||
                loopVarPsi.hasWriteAccesses(referenceSearcher, loopConditionPsi)
        if (loopVariableIsMutated) return null

        var conditionVariablesAreMutated = false
        condition.right.forEachDescendantOfType<JKFieldAccessExpression> { fieldAccess ->
            val psi = (fieldAccess.identifier.target as? JKLocalVariable)?.psi<PsiLocalVariable>() ?: return@forEachDescendantOfType
            if (psi.hasWriteAccesses(referenceSearcher, loopBodyPsi) ||
                psi.hasWriteAccesses(referenceSearcher, loopConditionPsi)
            ) {
                conditionVariablesAreMutated = true
            }
        }
        if (conditionVariablesAreMutated) return null

        val start = loopVar::initializer.detached()
        val bound = condition::right.detached().parenthesizeIfCompoundExpression()
        val range = forIterationRange(start, bound, isReversed, isInclusive)
        val explicitType =
            if (context.converter.settings.specifyLocalVariableTypeByDefault || loopVar.type.hasAnnotations)
                JKJavaPrimitiveType.INT
            else JKNoType
        val loopParameter =
            JKForLoopParameter(
                JKTypeElement(explicitType, loopVar.type::annotationList.detached()),
                loopVar::name.detached(),
                loopVar::annotationList.detached()
            )

        return JKForInStatement(loopParameter, range, loop::body.detached())
    }

    private fun JKExpression.incrementOrDecrementOperator(variable: JKLocalVariable): JKOperator? {
        val pair = when (this) {
            is JKPostfixExpression -> operator to expression
            is JKPrefixExpression -> operator to expression
            else -> return null
        }
        if ((pair.second as? JKFieldAccessExpression)?.identifier?.target != variable) return null
        return pair.first
    }

    private fun forIterationRange(
        start: JKExpression,
        bound: JKExpression,
        isReversed: Boolean,
        isInclusiveComparison: Boolean
    ): JKExpression {
        indicesIterationRange(start, bound, isReversed, isInclusiveComparison)?.also { return it }
        return when {
            isReversed -> downToExpression(
                start,
                convertBound(bound, if (isInclusiveComparison) 0 else +1),
                context
            )

            bound !is JKLiteralExpression && !isInclusiveComparison ->
                untilToExpression(
                    start,
                    convertBound(bound, 0),
                    context
                )

            else -> JKBinaryExpression(
                start,
                convertBound(bound, if (isInclusiveComparison) 0 else -1),
                JKKtOperatorImpl(
                    JKOperatorToken.RANGE,
                    typeFactory.types.nullableAny //todo range type
                )
            )
        }
    }

    private fun convertBound(bound: JKExpression, correction: Int): JKExpression {
        if (correction == 0) return bound

        if (bound is JKLiteralExpression && bound.type == JKLiteralExpression.LiteralType.INT) {
            val correctedLiteral = addCorrectionToIntLiteral(bound.literal, correction)

            if (correctedLiteral != null) {
                return JKLiteralExpression(correctedLiteral, bound.type)
            }
        }

        val sign = if (correction > 0) JKOperatorToken.PLUS else JKOperatorToken.MINUS
        return JKBinaryExpression(
            bound,
            JKLiteralExpression(abs(correction).toString(), JKLiteralExpression.LiteralType.INT),
            JKKtOperatorImpl(
                sign,
                typeFactory.types.int
            )
        )
    }

    private fun addCorrectionToIntLiteral(intLiteral: String, correction: Int): String? {
        require(!intLiteral.startsWith("-")) { "This function does not work with signed literals, but $intLiteral was supplied" }
        val numberWithRadix = extractRadix(intLiteral)
        val value = numberWithRadix.number.toIntOrNull(numberWithRadix.radix) ?: return null
        val fixedValue = (value + correction).toString(numberWithRadix.radix)
        return "${numberWithRadix.radixPrefix}${fixedValue}"
    }

    private val NumberWithRadix.radixPrefix: String
        get() = when (radix) {
            2 -> "0b"
            10 -> ""
            16 -> "0x"
            else -> error("Invalid radix for $this")
        }

    private fun indicesIterationRange(
        start: JKExpression,
        bound: JKExpression,
        isReversed: Boolean,
        isInclusiveComparison: Boolean
    ): JKExpression? {
        val collectionSizeExpression =
            if (isReversed) {
                if (!isInclusiveComparison) return null

                if ((bound as? JKLiteralExpression)?.literal?.toIntOrNull() != 0) return null

                if (start !is JKBinaryExpression) return null
                if (start.operator.token.text != "-") return null
                if ((start.right as? JKLiteralExpression)?.literal?.toIntOrNull() != 1) return null
                start.left
            } else {
                if (isInclusiveComparison) return null
                if ((start as? JKLiteralExpression)?.literal?.toIntOrNull() != 0) return null
                bound
            } as? JKQualifiedExpression ?: return null

        val indices = indicesByCollectionSize(collectionSizeExpression)
            ?: indicesByArrayLength(collectionSizeExpression)
            ?: return null

        return if (isReversed) {
            JKQualifiedExpression(
                indices,
                JKCallExpressionImpl(
                    symbolProvider.provideMethodSymbol("kotlin.collections.reversed"),
                    JKArgumentList()
                )
            )
        } else indices
    }


    private fun indicesByCollectionSize(javaSizeCall: JKQualifiedExpression): JKQualifiedExpression? {
        val methodCall = javaSizeCall.selector as? JKCallExpression ?: return null
        return if (methodCall.identifier.deepestFqName() == "java.util.Collection.size"
            && methodCall.arguments.arguments.isEmpty()
        ) toIndicesCall(javaSizeCall) else null
    }

    private fun indicesByArrayLength(javaSizeCall: JKQualifiedExpression): JKQualifiedExpression? {
        val methodCall = javaSizeCall.selector as? JKFieldAccessExpression ?: return null
        val receiverType = javaSizeCall.receiver.calculateType(typeFactory)
        if (methodCall.identifier.name == "length" && receiverType is JKJavaArrayType) {
            return toIndicesCall(javaSizeCall)
        }
        return null
    }

    private fun toIndicesCall(javaSizeCall: JKQualifiedExpression): JKQualifiedExpression? {
        if (javaSizeCall.psi == null) return null
        val selector = JKFieldAccessExpression(
            symbolProvider.provideFieldSymbol("kotlin.collections.indices")
        )
        return JKQualifiedExpression(javaSizeCall::receiver.detached(), selector)
    }
}

private class ForToWhileConverter(private val context: ConverterContext, private val symbolProvider: JKSymbolProvider) {
    fun convert(loop: JKJavaForLoopStatement): JKStatement {
        val whileBody = createWhileBody(loop)
        val condition =
            if (loop.condition !is JKStubExpression) loop::condition.detached()
            else JKLiteralExpression("true", JKLiteralExpression.LiteralType.BOOLEAN)
        val whileStatement = JKWhileStatement(condition, whileBody)

        if (loop.initializers.isEmpty()
            || loop.initializers.singleOrNull() is JKEmptyStatement
        ) return whileStatement

        val convertedFromForLoopSyntheticWhileStatement =
            JKKtConvertedFromForLoopSyntheticWhileStatement(
                loop::initializers.detached(),
                whileStatement
            )

        val notNeedParentBlock = loop.parent is JKBlock
                || loop.parent is JKLabeledExpression && loop.parent?.parent is JKBlock

        return when {
            loop.hasNameConflict() ->
                JKExpressionStatement(
                    runExpression(
                        convertedFromForLoopSyntheticWhileStatement,
                        symbolProvider
                    )
                )

            !notNeedParentBlock -> blockStatement(convertedFromForLoopSyntheticWhileStatement)
            else -> convertedFromForLoopSyntheticWhileStatement
        }
    }

    private fun createWhileBody(loop: JKJavaForLoopStatement): JKStatement {
        if (loop.updaters.singleOrNull() is JKEmptyStatement) return loop::body.detached()
        val continueStatementConverter = object : RecursiveConversion(context) {
            override fun applyToElement(element: JKTreeElement): JKTreeElement {
                if (element !is JKContinueStatement) return recurse(element)
                val elementPsi = element.psi<PsiContinueStatement>()!!
                if (elementPsi.findContinuedStatement()?.toContinuedLoop() != loop.psi<PsiForStatement>()) return recurse(element)
                val statements = loop.updaters.map { it.copyTreeAndDetach() } + element.copyTreeAndDetach()
                return if (element.parent is JKBlock)
                    JKBlockStatementWithoutBrackets(statements)
                else blockStatement(statements)
            }
        }

        val body = continueStatementConverter.applyToElement(loop::body.detached())

        val statements: List<JKStatement>
        if (body is JKBlockStatement) {
            val hasNameConflict = loop.initializers.any { initializer ->
                initializer is JKDeclarationStatement && initializer.declaredStatements.any { loopVar ->
                    loopVar is JKLocalVariable && body.statements.any { statement ->
                        statement is JKDeclarationStatement && statement.declaredStatements.any {
                            it is JKLocalVariable && it.name.value == loopVar.name.value
                        }
                    }
                }
            }

            statements =
                if (hasNameConflict) {
                    listOf(JKExpressionStatement(runExpression(body, symbolProvider))) + loop::updaters.detached()
                } else {
                    body.block::statements.detached() + loop::updaters.detached()
                }
        } else {
            statements = listOf(body as JKStatement) + loop::updaters.detached()
        }

        return blockStatement(statements)
    }

    private fun PsiStatement.toContinuedLoop(): PsiLoopStatement? = when (this) {
        is PsiLoopStatement -> this
        is PsiLabeledStatement -> statement?.toContinuedLoop()
        else -> null
    }

    private fun JKJavaForLoopStatement.hasNameConflict(): Boolean {
        val names = initializers.flatMap { it.declaredVariableNames() }
        if (names.isEmpty()) return false

        val factory = PsiElementFactory.getInstance(context.project)
        for (name in names) {
            val refExpr = try {
                factory.createExpressionFromText(name, psi) as? PsiReferenceExpression ?: return true
            } catch (e: IncorrectOperationException) {
                return true
            }
            if (refExpr.resolve() != null) return true
        }

        return (parent as? JKBlock)
            ?.statements
            ?.takeLastWhile { it != this }
            ?.any { statement ->
                statement.declaredVariableNames().any { it in names }
            } == true
    }

    private fun JKStatement.declaredVariableNames(): Collection<String> =
        when (this) {
            is JKDeclarationStatement ->
                declaredStatements.filterIsInstance<JKVariable>().map { it.name.value }

            is JKJavaForLoopStatement -> initializers.flatMap { it.declaredVariableNames() }
            else -> emptyList()
        }
}