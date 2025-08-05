// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import com.intellij.psi.SyntheticElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.config.LanguageFeature.EnumEntries
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.nj2k.*
import org.jetbrains.kotlin.nj2k.symbols.JKClassSymbol
import org.jetbrains.kotlin.nj2k.symbols.JKMultiverseMethodSymbol
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.types.JKClassType
import org.jetbrains.kotlin.nj2k.types.isEnumType
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

private const val ENUM_VALUES_METHOD_NAME = "values"
private const val ENUM_ENTRIES_PROPERTY_NAME = "entries"

class EnumSyntheticValuesMethodConversion(context: ConverterContext) : RecursiveConversion(context) {
    context(_: KaSession)
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKQualifiedExpression && element !is JKCallExpression) return recurse(element)
        if (!languageVersionSettings.supportsFeature(EnumEntries)) return recurse(element)

        val identifier = element.selector().identifier ?: return recurse(element)
        if (identifier.name != ENUM_VALUES_METHOD_NAME) return recurse(element)
        if (identifier.target !is SyntheticElement) return recurse(element)
        return if (element.isReceiverEnumType()) recurse(convert(element)) else recurse(element)
    }

    private fun JKTreeElement.selector(): JKTreeElement =
        if (this is JKQualifiedExpression) selector else this

    context(_: KaSession)
    private fun JKTreeElement.isReceiverEnumType(): Boolean =
        when (this) {
            is JKQualifiedExpression ->
                receiver.identifier?.isEnumType() == true

            is JKCallExpression ->
                identifier.receiverType.safeAs<JKClassType>()?.classReference?.isEnumType() == true

            else -> false
        }

    private fun convert(element: JKTreeElement): JKTreeElement {
        val enumClassSymbol = (element.selector().identifier as? JKMultiverseMethodSymbol)?.enumClassSymbol() ?: return element
        val entriesCall = JKQualifiedExpression(
            JKClassAccessExpression(enumClassSymbol),
            JKFieldAccessExpression(
                symbolProvider.provideFieldSymbol("${enumClassSymbol.fqName}.$ENUM_ENTRIES_PROPERTY_NAME")
            )
        ).withFormattingFrom(element)

        if (canChangeReturnTypeFromArrayToList(element)) {
            rebindArraySymbolsToListSymbols(element)
            return entriesCall
        }

        return entriesCall.callOn(symbolProvider.provideMethodSymbol("kotlin.collections.toTypedArray"))
    }

    private fun JKMultiverseMethodSymbol.enumClassSymbol(): JKClassSymbol? {
        val containingClass = target.containingClass ?: return null
        return symbolProvider.provideDirectSymbol(containingClass) as? JKClassSymbol
    }

    private fun canChangeReturnTypeFromArrayToList(element: JKTreeElement): Boolean {
        if (element.parent is JKForInStatement) return true
        if (element.isUsedAsAssignmentTarget()) return false
        if (element.parent is JKArrayAccessExpression) return true
        val nextCall = element.getNextCall() ?: return false
        return nextCall.isArrayGetCall() || nextCall.isArraySizeCall()
    }

    private fun JKTreeElement.isUsedAsAssignmentTarget(): Boolean {
        val upperParent = parents().take(2).firstIsInstanceOrNull<JKKtAssignmentStatement>()
        return upperParent?.field == parent
    }

    private fun JKTreeElement.getNextCall(): JKExpression? =
        parent.safeAs<JKQualifiedExpression>()?.selector

    // TODO remove in K2
    private fun JKExpression.isArrayGetCall(): Boolean =
        this is JKCallExpression && identifier.fqName == "kotlin.Array.get"

    private fun JKExpression.isArraySizeCall(): Boolean =
        this is JKFieldAccessExpression && identifier.fqName == "kotlin.Array.size"

    private fun rebindArraySymbolsToListSymbols(element: JKTreeElement) {
        val nextCall = element.getNextCall() ?: return

        when {
            nextCall.isArraySizeCall() ->
                (nextCall as JKFieldAccessExpression).identifier = symbolProvider.provideFieldSymbol("kotlin.collections.List.size")

            nextCall.isArrayGetCall() ->
                (nextCall as JKCallExpression).identifier = symbolProvider.provideMethodSymbol("kotlin.collections.List.get")
        }
    }
}
