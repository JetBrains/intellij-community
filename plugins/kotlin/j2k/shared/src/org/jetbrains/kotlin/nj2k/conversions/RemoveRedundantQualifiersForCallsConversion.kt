// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.identifier
import org.jetbrains.kotlin.nj2k.symbols.JKUniverseClassSymbol
import org.jetbrains.kotlin.nj2k.symbols.isStaticMember
import org.jetbrains.kotlin.nj2k.tree.JKCallExpression
import org.jetbrains.kotlin.nj2k.tree.JKClassAccessExpression
import org.jetbrains.kotlin.nj2k.tree.JKExpression
import org.jetbrains.kotlin.nj2k.tree.JKFieldAccessExpression
import org.jetbrains.kotlin.nj2k.tree.JKQualifiedExpression
import org.jetbrains.kotlin.nj2k.tree.JKThisExpression
import org.jetbrains.kotlin.nj2k.tree.JKTreeElement
import org.jetbrains.kotlin.nj2k.tree.withFormattingFrom

class RemoveRedundantQualifiersForCallsConversion(context: ConverterContext) : RecursiveConversion(context) {
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKQualifiedExpression) return recurse(element)
        val needRemoveQualifier = when (val receiver = element.receiver.receiverExpression()) {
            is JKClassAccessExpression -> receiver.identifier is JKUniverseClassSymbol
            is JKFieldAccessExpression, is JKCallExpression, is JKThisExpression -> {
                element.selector.identifier?.isStaticMember == true
            }

            else -> false
        }

        if (needRemoveQualifier) {
            element.invalidate()
            return recurse(element.selector.withFormattingFrom(element.receiver).withFormattingFrom(element))
        }
        return recurse(element)
    }

    private fun JKExpression.receiverExpression() = when (this) {
        is JKQualifiedExpression -> selector
        else -> this
    }
}