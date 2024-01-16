// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.j2k

import com.intellij.psi.*
import com.intellij.psi.controlFlow.ControlFlowFactory
import com.intellij.psi.controlFlow.ControlFlowUtil
import com.intellij.psi.controlFlow.LocalsOrMyInstanceFieldsControlFlowPolicy
import org.jetbrains.kotlin.j2k.ast.*


class SwitchConverter(private val codeConverter: CodeConverter) {
    fun convert(statement: PsiSwitchStatement): WhenStatement
            = WhenStatement(codeConverter.convertExpression(statement.expression), switchBodyToWhenEntries(statement.body))

    private class Case(val label: PsiSwitchLabelStatement?, val statements: List<PsiStatement>)

    private fun switchBodyToWhenEntries(body: PsiCodeBlock?): List<WhenEntry> {
        //TODO: this code is to be changed when continue in when is supported by Kotlin

        val cases = splitToCases(body)

        val result = ArrayList<WhenEntry>()
        var pendingSelectors = ArrayList<WhenEntrySelector>()
        var defaultSelector: WhenEntrySelector? = null
        var defaultEntry: WhenEntry? = null
        for ((i, case) in cases.withIndex()) {
            if (case.label == null) { // invalid switch - no case labels
                result.add(WhenEntry(listOf(ValueWhenEntrySelector(Expression.Empty).assignNoPrototype()), convertCaseStatementsToBody(cases, i)).assignNoPrototype())
                continue
            }
            val sel = codeConverter.convertStatement(case.label) as WhenEntrySelector
            if (case.label.isDefaultCase) defaultSelector = sel else pendingSelectors.add(sel)
            if (case.statements.isNotEmpty()) {
                val statement = convertCaseStatementsToBody(cases, i)
                if (pendingSelectors.isNotEmpty())
                    result.add(WhenEntry(pendingSelectors, statement).assignNoPrototype())
                if (defaultSelector != null)
                    defaultEntry = WhenEntry(listOf(defaultSelector), statement).assignNoPrototype()
                pendingSelectors = ArrayList()
                defaultSelector = null
            }
        }
        defaultEntry?.let(result::add)
        return result
    }

    private fun splitToCases(body: PsiCodeBlock?): List<Case> {
        val cases = ArrayList<Case>()
        if (body != null) {
            var currentLabel: PsiSwitchLabelStatement? = null
            var currentCaseStatements = ArrayList<PsiStatement>()

            fun flushCurrentCase() {
                if (currentLabel != null || currentCaseStatements.isNotEmpty()) {
                    cases.add(Case(currentLabel, currentCaseStatements))
                }
            }

            for (statement in body.statements) {
                if (statement is PsiSwitchLabelStatement) {
                    flushCurrentCase()
                    currentLabel = statement
                    currentCaseStatements = ArrayList()
                }
                else {
                    currentCaseStatements.add(statement)
                }
            }

            flushCurrentCase()
        }

        return cases
    }

    private fun convertCaseStatements(statements: List<PsiStatement>, allowBlock: Boolean = true): List<Statement> {
        val statementsToKeep = statements.filter { !isSwitchBreak(it) }
        if (allowBlock && statementsToKeep.size == 1) {
            val block = statementsToKeep.single() as? PsiBlockStatement
            if (block != null) {
                return listOf(codeConverter.convertBlock(block.codeBlock, true) { !isSwitchBreak(it) })
            }
        }
        return statementsToKeep.map { codeConverter.convertStatement(it) }
    }

    private fun convertCaseStatements(cases: List<Case>, caseIndex: Int, allowBlock: Boolean = true): List<Statement> {
        val case = cases[caseIndex]
        val fallsThrough = if (caseIndex == cases.lastIndex) {
            false
        }
        else {
            val block = case.statements.singleOrNull() as? PsiBlockStatement
            val statements = block?.codeBlock?.statements?.toList() ?: case.statements
            statements.fallsThrough()
        }
        return if (fallsThrough) // we fall through into the next case
            convertCaseStatements(case.statements, allowBlock = false) + convertCaseStatements(cases, caseIndex + 1, allowBlock = false)
        else
            convertCaseStatements(case.statements, allowBlock)
    }

    private fun convertCaseStatementsToBody(cases: List<Case>, caseIndex: Int): Statement {
        val statements = convertCaseStatements(cases, caseIndex)
        return if (statements.size == 1)
            statements.single()
        else
            Block.of(statements).assignNoPrototype()
    }

    private fun isSwitchBreak(statement: PsiStatement) = statement is PsiBreakStatement && statement.labelIdentifier == null

    private fun List<PsiStatement>.fallsThrough(): Boolean {
        for (statement in this) {
            when (statement) {
                is PsiBreakStatement -> return false
                is PsiContinueStatement -> return false
                is PsiReturnStatement -> return false
                is PsiThrowStatement -> return false
                is PsiSwitchStatement -> if (!statement.canCompleteNormally()) return false
                is PsiIfStatement -> if (!statement.canCompleteNormally()) return false
            }
        }
        return true
    }

    private fun PsiElement.canCompleteNormally(): Boolean {
        val controlFlow = ControlFlowFactory.getInstance(project).getControlFlow(this, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance())
        val startOffset = controlFlow.getStartOffset(this)
        val endOffset = controlFlow.getEndOffset(this)
        return startOffset == -1 || endOffset == -1 || ControlFlowUtil.canCompleteNormally(controlFlow, startOffset, endOffset)
    }
}
