// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.asStatement
import org.jetbrains.kotlin.nj2k.tree.JKBlockStatement
import org.jetbrains.kotlin.nj2k.tree.JKBlockStatementWithoutBrackets
import org.jetbrains.kotlin.nj2k.tree.JKExpressionStatement
import org.jetbrains.kotlin.nj2k.tree.JKKtConvertedFromForLoopSyntheticWhileStatement
import org.jetbrains.kotlin.nj2k.tree.JKLabeledExpression
import org.jetbrains.kotlin.nj2k.tree.JKTreeElement
import org.jetbrains.kotlin.nj2k.tree.detached
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class LabeledStatementConversion(context: ConverterContext) : RecursiveConversion(context) {
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKExpressionStatement) return recurse(element)
        val labeledStatement = element.expression as? JKLabeledExpression ?: return recurse(element)
        val convertedFromForLoopSyntheticWhileStatement = labeledStatement.statement
            .safeAs<JKBlockStatement>()
            ?.block
            ?.statements
            ?.singleOrNull()
            ?.safeAs<JKKtConvertedFromForLoopSyntheticWhileStatement>() ?: return recurse(element)

        return recurse(
            JKBlockStatementWithoutBrackets(
                convertedFromForLoopSyntheticWhileStatement::variableDeclarations.detached() +
                        JKLabeledExpression(
                            convertedFromForLoopSyntheticWhileStatement::whileStatement.detached(),
                            labeledStatement::labels.detached()
                        ).asStatement()
            )
        )
    }
}