// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveApplicableConversionWithState
import org.jetbrains.kotlin.nj2k.asStatement
import org.jetbrains.kotlin.nj2k.tree.*


class YieldStatementConversion(context: NewJ2kConverterContext) : RecursiveApplicableConversionWithState<Boolean>(context, false) {
    override fun applyToElement(element: JKTreeElement, state: Boolean/*is yield allowed*/): JKTreeElement {
        when (element) {
            is JKKtWhenExpression -> return recurse(element, true)
            is JKMethod -> return recurse(element, false)
            is JKLambdaExpression -> return recurse(element, false)
            !is JKJavaYieldStatement -> return recurse(element, state)
        }
        element.invalidate()

        check(element is JKJavaYieldStatement)
        val newElement = if (state) {
            element.expression.asStatement()
        } else {
            JKErrorStatement(element.psi, "yield is not allowed outside switch expression")
        }

        return recurse(newElement, false)
    }
}