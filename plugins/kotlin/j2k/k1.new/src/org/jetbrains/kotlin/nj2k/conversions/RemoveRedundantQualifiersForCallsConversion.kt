// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveApplicableConversionBase
import org.jetbrains.kotlin.nj2k.identifier
import org.jetbrains.kotlin.nj2k.symbols.JKUniverseClassSymbol
import org.jetbrains.kotlin.nj2k.symbols.isStaticMember
import org.jetbrains.kotlin.nj2k.tree.*

internal class RemoveRedundantQualifiersForCallsConversion(context: NewJ2kConverterContext) : RecursiveApplicableConversionBase(context) {
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