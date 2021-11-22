// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.psi
import org.jetbrains.kotlin.nj2k.tree.*


class BlockToRunConversion(context: NewJ2kConverterContext) : RecursiveApplicableConversionBase(context) {
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKBlockStatement) return recurse(element)

        if (element.parent !is JKBlock) return recurse(element)

        if(element.parents().none { it is JKDeclaration || it is JKLambdaExpression }) return recurse(element)

        element.invalidate()
        val lambda = JKLambdaExpression(
            JKBlockStatement(element.block),
            emptyList()
        )
        val call = JKCallExpressionImpl(
            symbolProvider.provideMethodSymbol("kotlin.run"),
            JKArgumentList(lambda)
        )
        return recurse(JKExpressionStatement(call).withFormattingFrom(element))
    }

}