// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.statements
import org.jetbrains.kotlin.nj2k.tree.*

class MarkConditionalStatementsWithLiftedReturnsConversion(context: NewJ2kConverterContext) : RecursiveConversion(context) {
    context(KtAnalysisSession)
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKIfElseStatement && element !is JKKtWhenBlock) return recurse(element)
        if (element is JKIfElseStatement) {
            if (element.elseBranch.statements.isNotEmpty() && element.thenBranch.statements.isNotEmpty() &&
                element.thenBranch.statements.all {it is JKReturnStatement} && element.elseBranch.statements.all {it is JKReturnStatement}
            ) {
                element.hasLiftedReturn = true
            }
        }
        else if (element is JKKtWhenBlock) {
            element.hasLiftedReturn = when {
                element.cases.isEmpty() -> false
                element.cases.none { it.labels.isNotEmpty() && it.labels.any { label -> label is JKKtElseWhenLabel } } -> false
                else -> element.cases.all { case -> case.statement is JKReturnStatement }
            }
        }

        return recurse(element)
    }
}