// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.asStatement
import org.jetbrains.kotlin.nj2k.statements
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

/**
 * A code style conversion (disabled in basic mode) that simplifies conditionals that always end in return statements
 * by lifting out the return
 *
 * This is a J2K equivalent of `LiftReturnOrAssignmentInspection`.
 */
class LiftReturnsConversion(context: NewJ2kConverterContext) : RecursiveConversion(context) {
    override fun isEnabledInBasicMode(): Boolean = false

    context(KtAnalysisSession)
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKIfElseStatement && element !is JKKtWhenBlock && element !is JKKtTryExpression) return recurse(element)
        when (element) {
            is JKIfElseStatement -> {
                val liftReturn = if (element.hasLiftedReturn) true else element.isLiftedReturnStatement()
                if (liftReturn) {
                    return recurse(element.applyConversion())
                }
            }

            is JKKtWhenBlock -> {
                val liftReturn = if (element.hasLiftedReturn) true else element.isLiftedReturnStatement()
                if (liftReturn) {
                    return recurse(element.applyConversion())
                }
            }
            is JKKtTryExpression -> {
                val liftReturn = if (element.hasLiftedReturn) true else element.isLiftedReturnStatement()
                if (liftReturn) {
                    val xx = element.applyConversion()
                    return recurse(xx)
                }
            }
        }
        return recurse(element)
    }

    private fun JKIfElseStatement.isLiftedReturnStatement(): Boolean =
        when {
            thenBranch.statements.size != 1 || elseBranch.statements.size != 1 -> false
            !statementIsEligible(thenBranch.statements[0]) -> false
            !statementIsEligible(elseBranch.statements[0]) -> false
            else -> {
                hasLiftedReturn = true
                true
            }
        }

    private fun JKKtWhenBlock.isLiftedReturnStatement(): Boolean =
        when {
            cases.isEmpty() -> false
            cases.none {
                it.labels.isNotEmpty()
                        && it.labels.any { label -> label is JKKtElseWhenLabel }
            } -> false

            cases.any { case -> !statementIsEligible(case.statement) } -> false
            else -> {
                hasLiftedReturn = true
                true
            }
        }

    private fun JKKtTryExpression.isLiftedReturnStatement(): Boolean =
        when {
            tryBlock.statements.size != 1 || !statementIsEligible(tryBlock.statements[0]) -> false
            catchSections.any { it.block.statements.size != 1 || !statementIsEligible(it.block.statements[0]) } -> false
            else -> {
                hasLiftedReturn = true
                true
            }
        }

    private fun JKKtWhenBlock.applyConversion(): JKTreeElement {
        // to be eligible for conversion, these statements must be a return OR a recursively lifted return
        val newCases: List<JKKtWhenCase> = cases.map {
            it.statement = when (val state = it.statement) {
                is JKReturnStatement -> state::expression.detached().asStatement().withCommentsFrom(state)
                is JKIfElseStatement -> JKExpressionStatement(state.applyConversion() as JKExpression)
                is JKKtWhenBlock -> JKExpressionStatement(state.applyConversion() as JKExpression)
                else -> it.statement.detached(this)
            }
            it.detached(this).withFormattingFrom(it)
        }
        val newExpression = JKKtWhenExpression(expression.detached(this), newCases, expressionType = null).withFormattingFrom(this)
        return if (parentIsLiftedOrReturn(this.safeAs<JKTreeElement>())) newExpression else JKReturnStatement(newExpression)
    }

    private fun JKKtTryExpression.applyConversion(): JKTreeElement {
        val tryExpression = when (val tryStatement = tryBlock.statements[0]) {
            is JKReturnStatement -> tryStatement::expression.detached().withFormattingFrom(tryStatement)
            is JKIfElseStatement -> tryStatement.applyConversion() as JKExpression
            is JKKtWhenBlock -> tryStatement.applyConversion() as JKExpression
            else -> return this // should never happen
        }
        tryBlock.statements = listOf(tryExpression.asStatement())
        val newCatchSections: List<JKKtTryCatchSection> = catchSections.map {
            val newStatement = when (val statement = it.block.statements[0]) {
                is JKReturnStatement -> statement::expression.detached().asStatement().withCommentsFrom(statement)
                is JKIfElseStatement -> JKExpressionStatement(statement.applyConversion() as JKExpression)
                is JKKtWhenBlock -> JKExpressionStatement(statement.applyConversion() as JKExpression)
                else -> statement.detached(this)
            }
            it.block.statements = listOf(newStatement)
            it.detached(this).withFormattingFrom(it)
        }
        val newExpression = JKKtTryExpression(
            tryBlock.detached(this).withFormattingFrom(tryBlock),
            finallyBlock.detached(this),
            newCatchSections
        ).withFormattingFrom(this)
        return if (parentIsLiftedOrReturn(this)) newExpression else JKReturnStatement(newExpression)
    }

    private fun JKIfElseStatement.applyConversion(): JKTreeElement {
        // to be eligible for conversion, these statements must be a return OR a recursively lifted return
        val thenStatement = thenBranch.statements[0]
        val elseStatement = elseBranch.statements[0]
        val hasBracketedThenStatement = thenBranch is JKBlockStatement && thenBranch !is JKBlockStatementWithoutBrackets
        val hasBracketedElseStatement = elseBranch is JKBlockStatement && elseBranch !is JKBlockStatementWithoutBrackets

        val thenExpression = when (thenStatement) {
            is JKReturnStatement -> thenStatement::expression.detached().withFormattingFrom(thenStatement)
            is JKIfElseStatement -> thenStatement.applyConversion() as JKExpression
            is JKKtWhenBlock -> thenStatement.applyConversion() as JKExpression
            else -> return this
        }
        val elseExpression = when (elseStatement) {
            is JKReturnStatement -> elseStatement::expression.detached().withFormattingFrom(elseStatement)
            is JKIfElseStatement -> elseStatement.applyConversion() as JKExpression
            is JKKtWhenBlock -> elseStatement.applyConversion() as JKExpression
            else -> return this
        }
        val newExpression = JKIfElseExpression(
            condition.detached(this),
            thenExpression.withFormattingFrom(thenBranch),
            elseExpression.withFormattingFrom(elseBranch)
        ).withFormattingFrom(this)
        newExpression.hasBracketedThenBranch = hasBracketedThenStatement
        newExpression.hasBracketedElseBranch = hasBracketedElseStatement
        return if (parentIsLiftedOrReturn(this)) newExpression else JKReturnStatement(newExpression)
    }

    private fun parentIsLiftedOrReturn(element: JKTreeElement?): Boolean =
        element != null && (element.parentOfType<JKKtWhenBlock>()?.hasLiftedReturn == true
                || element.parentOfType<JKIfElseStatement>()?.hasLiftedReturn == true
                || element.parentOfType<JKReturnStatement>() != null)

    private fun statementIsEligible(statement: JKStatement?): Boolean = statement != null &&
            (statement is JKIfElseStatement && statement.isLiftedReturnStatement())
            || (statement is JKKtWhenBlock && statement.isLiftedReturnStatement())
            || (statement is JKReturnStatement)
            || (statement.safeAs<JKExpressionStatement>()?.expression is JKThrowExpression)
}