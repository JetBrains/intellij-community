// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import com.intellij.psi.PsiElement
import com.intellij.psi.controlFlow.ControlFlowFactory
import com.intellij.psi.controlFlow.ControlFlowUtil
import com.intellij.psi.controlFlow.LocalsOrMyInstanceFieldsControlFlowPolicy
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.blockStatement
import org.jetbrains.kotlin.nj2k.runExpression
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.util.takeWhileInclusive

class SwitchToWhenConversion(context: ConverterContext) : RecursiveConversion(context) {
    context(_: KaSession)
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKJavaSwitchBlock) return recurse(element)
        element.invalidate()
        element.cases.forEach { case ->
            case.statements.forEach { it.detach(case) }
            if (case is JKJavaLabelSwitchCase) {
                case::labels.detached()
            }
        }
        val cases = switchCasesToWhenCases(element.cases).moveElseCaseToTheEnd()
        val whenBlock = when (element) {
            is JKJavaSwitchExpression -> JKKtWhenExpression(element.expression, cases, element.calculateType(typeFactory))
            is JKJavaSwitchStatement -> JKKtWhenStatement(element.expression, cases)
            else -> error("Unexpected class ${element::class.simpleName}")
        }

        return recurse(whenBlock.withFormattingFrom(element))
    }

    private fun List<JKKtWhenCase>.moveElseCaseToTheEnd(): List<JKKtWhenCase> =
        sortedBy { case -> case.labels.any { it is JKKtElseWhenLabel } }

    private fun switchCasesToWhenCases(cases: List<JKJavaSwitchCase>): List<JKKtWhenCase> {
        if (cases.isEmpty()) return emptyList()

        val statements = if (cases.first() is JKJavaArrowSwitchLabelCase) cases.first().statements else {
            cases
                .takeWhileInclusive { it.statements.fallsThrough() }
                .flatMap { it.statements }
                .takeWhileInclusive { statement -> statement.singleListOrBlockStatements().none { isSwitchBreakOrYield(it) } }
                .mapNotNull { statement ->
                    when (statement) {
                        is JKBlockStatement -> blockStatement(
                            statement.block.statements
                                .takeWhileInclusive { !isSwitchBreakOrYield(it) }
                                .mapNotNull { handleBreakOrYield(it) }
                        ).withFormattingFrom(statement)

                        else -> handleBreakOrYield(statement)
                    }
                }
        }

        val javaLabels = cases
            .takeWhileInclusive { it.statements.isEmpty() }

        val statementLabels = javaLabels
            .filterIsInstance<JKJavaLabelSwitchCase>()
            .flatMap { it.labels }
            .map { JKKtValueWhenLabel(it) }

        val elseLabel = javaLabels
            .find { it is JKJavaDefaultSwitchCase }
            ?.let { JKKtElseWhenLabel() }
        val elseWhenCase = elseLabel?.let { label ->
            JKKtWhenCase(listOf(label), statements.map { it.copyTreeAndDetach() }.singleBlockOrWrapToRun())
        }
        val mainWhenCase =
            if (statementLabels.isNotEmpty()) {
                JKKtWhenCase(statementLabels, statements.singleBlockOrWrapToRun())
            } else null
        return listOfNotNull(mainWhenCase) +
                listOfNotNull(elseWhenCase) +
                switchCasesToWhenCases(cases.drop(javaLabels.size))

    }

    private fun handleBreakOrYield(statement: JKStatement) = when {
        isSwitchBreak(statement) -> null
        else -> statement.copyTreeAndDetach()
    }

    private fun List<JKStatement>.singleBlockOrWrapToRun(): JKStatement =
        singleOrNull() ?: blockStatement(map { statement ->
            when (statement) {
                is JKBlockStatement -> JKExpressionStatement(runExpression(statement, symbolProvider))
                else -> statement
            }
        })

    private fun JKStatement.singleListOrBlockStatements(): List<JKStatement> =
        when (this) {
            is JKBlockStatement -> block.statements
            else -> listOf(this)
        }

    private fun isSwitchBreak(statement: JKStatement) =
        statement is JKBreakStatement && statement.label is JKLabelEmpty

    private fun isSwitchBreakOrYield(statement: JKStatement) =
        isSwitchBreak(statement) || statement is JKJavaYieldStatement

    private fun List<JKStatement>.fallsThrough(): Boolean =
        all { it.fallsThrough() }

    private fun JKStatement.fallsThrough(): Boolean =
        when {
            this.isThrowStatement() ||
                    this is JKBreakStatement ||
                    this is JKReturnStatement ||
                    this is JKContinueStatement -> false

            this is JKBlockStatement -> block.statements.fallsThrough()
            this is JKIfElseStatement ||
                    this is JKJavaSwitchBlock ||
                    this is JKKtWhenBlock ->
                psi?.canCompleteNormally() == true

            else -> true
        }

    private fun JKStatement.isThrowStatement(): Boolean =
        (this as? JKExpressionStatement)?.expression is JKThrowExpression

    private fun PsiElement.canCompleteNormally(): Boolean {
        val controlFlow =
            ControlFlowFactory.getInstance(project).getControlFlow(this, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance())
        val startOffset = controlFlow.getStartOffset(this)
        val endOffset = controlFlow.getEndOffset(this)
        return startOffset == -1 || endOffset == -1 || ControlFlowUtil.canCompleteNormally(controlFlow, startOffset, endOffset)
    }
}