// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveConversionWithData
import org.jetbrains.kotlin.nj2k.asStatement
import org.jetbrains.kotlin.nj2k.tree.*

class YieldStatementConversion(context: NewJ2kConverterContext) :
    RecursiveConversionWithData<Boolean>(context, initialData = false) {

    override fun applyToElement(element: JKTreeElement, data: Boolean /* is yield allowed */): JKTreeElement {
        when (element) {
            is JKKtWhenExpression -> return recurse(element, data = true)
            is JKMethod -> return recurse(element, data = false)
            is JKLambdaExpression -> return recurse(element, data = false)
            !is JKJavaYieldStatement -> return recurse(element, data)
        }
        element.invalidate()

        check(element is JKJavaYieldStatement)
        val newElement = if (data) {
            element.expression.asStatement()
        } else {
            JKErrorStatement(element.psi, "yield is not allowed outside switch expression")
        }

        return recurse(newElement, data = false)
    }
}