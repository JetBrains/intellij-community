// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.tree.JKBlock
import org.jetbrains.kotlin.nj2k.tree.JKClass
import org.jetbrains.kotlin.nj2k.tree.JKConstructorImpl
import org.jetbrains.kotlin.nj2k.tree.JKDelegationConstructorCall
import org.jetbrains.kotlin.nj2k.tree.JKExpressionStatement
import org.jetbrains.kotlin.nj2k.tree.JKMethod
import org.jetbrains.kotlin.nj2k.tree.JKStubExpression
import org.jetbrains.kotlin.nj2k.tree.JKTreeElement
import org.jetbrains.kotlin.nj2k.tree.parentOfType
import org.jetbrains.kotlin.nj2k.tree.withFormattingFrom

class ConstructorConversion(context: ConverterContext) : RecursiveConversion(context) {
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKMethod) return recurse(element)
        val outerClass = element.parentOfType<JKClass>() ?: return recurse(element)
        if (element.name.value != outerClass.name.value) return recurse(element)

        element.invalidate()
        val delegationCall = lookupDelegationCall(element.block) ?: JKStubExpression()

        return JKConstructorImpl(
            element.name,
            element.parameters,
            element.block,
            delegationCall,
            element.annotationList,
            element.otherModifierElements,
            element.visibilityElement,
            element.modalityElement
        ).also {
            symbolProvider.transferSymbol(it, element)
        }.withFormattingFrom(element)
    }

    private fun lookupDelegationCall(block: JKBlock): JKDelegationConstructorCall? {
        val firstStatement = block.statements.firstOrNull() ?: return null
        val expressionStatement = firstStatement as? JKExpressionStatement ?: return null
        val expression = expressionStatement.expression as? JKDelegationConstructorCall ?: return null
        block.statements -= expressionStatement
        expressionStatement.invalidate()
        return expression
    }
}