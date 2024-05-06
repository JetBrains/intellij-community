// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.asStatement
import org.jetbrains.kotlin.nj2k.tree.*

class RemoveLiftedReturnStatementsConversion(context: NewJ2kConverterContext) : RecursiveConversion(context) {
    override fun isEnabledInBasicMode(): Boolean = false
    context(KtAnalysisSession)
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKReturnStatement) return recurse(element)
        if (!element.returnShouldBeLifted()) return recurse(element)
        element.invalidate()
        return recurse(element.expression.asStatement())
    }

    private fun JKReturnStatement.returnShouldBeLifted(): Boolean {
        var parent = parent
        while (parent != null) {
            if (parent is JKKtWhenBlock) return parent.hasLiftedReturn
            if (parent is JKIfElseStatement) return parent.hasLiftedReturn
            parent = parent.parent
        }
        return false
    }
}