// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions


import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.asStatement
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.types.JKClassType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class ReturnStatementInLambdaExpressionConversion(context: ConverterContext) : RecursiveConversion(context) {
    companion object {
        const val DEFAULT_LABEL_NAME = "label"
    }

    context(_: KaSession)
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKLambdaExpression) return recurse(element)
        val statement = element.statement
        if (statement is JKReturnStatement) {
            element.statement = statement::expression.detached().asStatement()
            return recurse(element)
        }
        if (statement is JKBlockStatement) {
            val statements = statement.block.statements
            val last = statements.lastOrNull()
            if (last is JKReturnStatement) {
                statement.block.statements -= last
                statement.block.statements += last::expression.detached().asStatement()
            }
        }
        val parentMethodName = element.parent?.parent?.parent.safeAs<JKCallExpression>()?.identifier?.name
        val samTypeName = element.functionalType.type.safeAs<JKClassType>()?.classReference?.name
        val implicitLabel = parentMethodName ?: samTypeName
        if (implicitLabel != null) {
            applyLabelToAllReturnStatements(statement, element, implicitLabel)
            return recurse(element)
        }
        val atLeastOneReturnStatementExists = applyLabelToAllReturnStatements(statement, element, DEFAULT_LABEL_NAME)
        return if (atLeastOneReturnStatementExists) {
            JKLabeledExpression(
                recurse(element.copyTreeAndDetach()).asStatement(),
                listOf(JKNameIdentifier(DEFAULT_LABEL_NAME))
            )
        } else recurse(element)
    }

    private fun applyLabelToAllReturnStatements(
        statement: JKStatement,
        lambdaExpression: JKLambdaExpression,
        label: String
    ): Boolean {
        var atLeastOneReturnStatementExists = false
        fun addLabelToReturnStatement(returnStatement: JKReturnStatement) {
            if (returnStatement.label is JKLabelEmpty && returnStatement.parentOfType<JKLambdaExpression>() == lambdaExpression) {
                atLeastOneReturnStatementExists = true
                returnStatement.label = JKLabelText(JKNameIdentifier(label))
            }
        }

        fun fixAllReturnStatements(element: JKTreeElement): JKTreeElement {
            if (element !is JKReturnStatement) return applyRecursive(element, ::fixAllReturnStatements)
            addLabelToReturnStatement(element)
            return applyRecursive(element, ::fixAllReturnStatements)
        }


        applyRecursive(statement, ::fixAllReturnStatements)
        return atLeastOneReturnStatementExists
    }
}