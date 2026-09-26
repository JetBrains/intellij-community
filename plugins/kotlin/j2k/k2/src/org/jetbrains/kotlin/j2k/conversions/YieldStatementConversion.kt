// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.conversions

import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.j2k.RecursiveConversionWithData
import org.jetbrains.kotlin.j2k.asStatement
import org.jetbrains.kotlin.j2k.tree.JKErrorStatement
import org.jetbrains.kotlin.j2k.tree.JKJavaYieldStatement
import org.jetbrains.kotlin.j2k.tree.JKKtWhenExpression
import org.jetbrains.kotlin.j2k.tree.JKLambdaExpression
import org.jetbrains.kotlin.j2k.tree.JKMethod
import org.jetbrains.kotlin.j2k.tree.JKTreeElement

class YieldStatementConversion(context: ConverterContext) : RecursiveConversionWithData<Boolean>(context, initialData = false) {
    override fun applyToElement(element: JKTreeElement, data: Boolean /* is yield allowed */): JKTreeElement {
        when (element) {
            is JKKtWhenExpression -> return recurse(element, data = true)
            is JKMethod -> return recurse(element, data = false)
            is JKLambdaExpression -> return recurse(element, data = false)
            !is JKJavaYieldStatement -> return recurse(element, data)
        }
        element.invalidate()

        val newElement = if (data) {
            element.expression.asStatement()
        } else {
            JKErrorStatement(element.psi, "yield is not allowed outside switch expression")
        }

        return recurse(newElement, data = false)
    }
}