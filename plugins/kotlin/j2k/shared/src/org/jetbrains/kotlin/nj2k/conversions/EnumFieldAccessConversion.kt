// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import com.intellij.psi.PsiEnumConstant
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.symbols.JKClassSymbol
import org.jetbrains.kotlin.nj2k.symbols.JKFieldSymbol
import org.jetbrains.kotlin.nj2k.symbols.JKMultiverseFieldSymbol
import org.jetbrains.kotlin.nj2k.symbols.JKMultiverseKtEnumEntrySymbol
import org.jetbrains.kotlin.nj2k.symbols.JKUniverseFieldSymbol
import org.jetbrains.kotlin.nj2k.tree.JKClass
import org.jetbrains.kotlin.nj2k.tree.JKClassAccessExpression
import org.jetbrains.kotlin.nj2k.tree.JKEnumConstant
import org.jetbrains.kotlin.nj2k.tree.JKFieldAccessExpression
import org.jetbrains.kotlin.nj2k.tree.JKQualifiedExpression
import org.jetbrains.kotlin.nj2k.tree.JKTreeElement
import org.jetbrains.kotlin.nj2k.tree.parentOfType
import org.jetbrains.kotlin.psi.psiUtil.containingClass

/**
 * Adds an enum class qualifier to enum entry references.
 * TODO is this conversion still needed?
 */
class EnumFieldAccessConversion(context: ConverterContext) : RecursiveConversion(context) {
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKFieldAccessExpression) return recurse(element)
        if ((element.parent as? JKQualifiedExpression)?.selector == element) return recurse(element)
        val enumsClassSymbol = element.identifier.enumClassSymbol() ?: return recurse(element)

        return recurse(
            JKQualifiedExpression(
                JKClassAccessExpression(enumsClassSymbol),
                JKFieldAccessExpression(element.identifier)
            )
        )
    }

    private fun JKFieldSymbol.enumClassSymbol(): JKClassSymbol? {
        return when (this) {
            is JKMultiverseFieldSymbol -> {
                val enumClass = (target as? PsiEnumConstant)?.containingClass ?: return null
                symbolProvider.provideDirectSymbol(enumClass) as? JKClassSymbol
            }

            is JKMultiverseKtEnumEntrySymbol -> {
                val enumClass = target.containingClass() ?: return null
                symbolProvider.provideDirectSymbol(enumClass) as? JKClassSymbol
            }

            is JKUniverseFieldSymbol -> {
                val enumClass = (target as? JKEnumConstant)?.parentOfType<JKClass>() ?: return null
                symbolProvider.provideUniverseSymbol(enumClass)
            }

            else -> null
        }
    }
}