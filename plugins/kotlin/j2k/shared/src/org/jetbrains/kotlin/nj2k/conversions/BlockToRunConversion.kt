// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.runExpression
import org.jetbrains.kotlin.nj2k.tree.*

class BlockToRunConversion(context: ConverterContext) : RecursiveConversion(context) {
    context(_: KaSession)
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKBlockStatement) return recurse(element)
        if (element.parent !is JKBlock) return recurse(element)
        if (element.parents().none { it is JKDeclaration || it is JKLambdaExpression }) return recurse(element)
        element.invalidate()
        val call = runExpression(JKBlockStatement(element.block), symbolProvider)
        return recurse(JKExpressionStatement(call).withFormattingFrom(element))
    }
}