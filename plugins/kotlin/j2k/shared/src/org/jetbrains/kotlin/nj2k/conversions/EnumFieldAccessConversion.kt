// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import com.intellij.psi.PsiEnumConstant
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.symbols.*
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass

class EnumFieldAccessConversion(context: NewJ2kConverterContext) : RecursiveConversion(context) {
    context(KtAnalysisSession)
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
        return when {
            this is JKMultiverseFieldSymbol && target is PsiEnumConstant ->
                symbolProvider.provideDirectSymbol(target.containingClass ?: return null) as? JKClassSymbol

            this is JKMultiverseKtEnumEntrySymbol ->
                symbolProvider.provideDirectSymbol(target.containingClass() ?: return null) as? JKClassSymbol

            this is JKUniverseFieldSymbol && target is JKEnumConstant ->
                symbolProvider.provideUniverseSymbol(target.parentOfType<JKClass>() ?: return null)

            else -> null
        }
    }
}