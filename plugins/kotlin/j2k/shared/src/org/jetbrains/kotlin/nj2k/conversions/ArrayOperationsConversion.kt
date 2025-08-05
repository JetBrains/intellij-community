// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.symbols.JKUniverseFieldSymbol
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.types.JKJavaArrayType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs


class ArrayOperationsConversion(context: ConverterContext) : RecursiveConversion(context) {
    context(_: KaSession)
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKQualifiedExpression) return recurse(element)
        val selector = element.selector as? JKFieldAccessExpression ?: return recurse(element)
        if (element.receiver.isArrayOrVarargTypeParameter() && selector.identifier.name == "length") {
            val sizeCall =
                JKFieldAccessExpression(
                    symbolProvider.provideFieldSymbol("kotlin.Array.size")
                )
            element.selector = sizeCall
        }
        return recurse(element)
    }

    private fun JKExpression.isArrayOrVarargTypeParameter(): Boolean {
        if (calculateType(typeFactory) is JKJavaArrayType) return true
        val parameter =
            safeAs<JKFieldAccessExpression>()
                ?.identifier
                .safeAs<JKUniverseFieldSymbol>()
                ?.target
                ?.safeAs<JKParameter>()
        return parameter?.isVarArgs == true
    }
}