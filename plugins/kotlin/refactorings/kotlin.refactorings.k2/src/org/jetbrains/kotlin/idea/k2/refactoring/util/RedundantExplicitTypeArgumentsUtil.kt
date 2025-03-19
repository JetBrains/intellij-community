// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.util

import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.KaDefinitelyNotNullType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.end
import org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.range
import org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.start
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

private val INLINE_REIFIED_FUNCTIONS_WITH_INSIGNIFICANT_TYPE_ARGUMENTS: Set<String> = setOf("kotlin.arrayOf")

fun KaSession.areTypeArgumentsRedundant(
    typeArgumentList: KtTypeArgumentList,
    approximateFlexible: Boolean = false,
): Boolean {
    val callExpression = typeArgumentList.parent as? KtCallExpression ?: return false
    val symbol = callExpression.resolveToCall()?.successfulFunctionCallOrNull()?.symbol ?: return false
    if (isInlineReifiedFunction(symbol)) return false
    val newCallExpression = buildCallExpressionWithoutTypeArgs(callExpression) ?: return false
    return areTypeArgumentsEqual(callExpression, newCallExpression, approximateFlexible)
}

private fun buildCallExpressionWithoutTypeArgs(element: KtCallExpression): KtCallExpression? {
    val context = findContextToAnalyze(element) ?: return null
    val typeArgumentListRange = element.typeArgumentList?.textRange ?: return null
    val contextStartOffset = context.range.start

    val textWithoutTypeArgs = context.text.removeRange(
        typeArgumentListRange.start - contextStartOffset,
        typeArgumentListRange.end - contextStartOffset,
    )

    val (prefix, suffix) = if (context.parent !is KtClassBody) {
        "object Obj {" to "}"
    } else "" to ""

    val codeFragment = KtPsiFactory(
        element.project,
        markGenerated = false,
    ).createBlockCodeFragment("$prefix$textWithoutTypeArgs$suffix", context)

    return codeFragment.findElementAt(typeArgumentListRange.start + prefix.length - contextStartOffset)?.parentOfType()
}

private fun KaSession.isInlineReifiedFunction(symbol: KaFunctionSymbol): Boolean {
    if (symbol !is KaNamedFunctionSymbol) return false
    return symbol.importableFqName?.asString() !in INLINE_REIFIED_FUNCTIONS_WITH_INSIGNIFICANT_TYPE_ARGUMENTS &&
            (symbol.isInline && symbol.typeParameters.any { it.isReified })
}

private fun findContextToAnalyze(
    expression: KtExpression,
): KtExpression? {
    for (element in expression.parentsWithSelf) {
        when (element) {
            is KtFunctionLiteral -> continue
            is KtParameter -> continue
            is KtPropertyAccessor -> continue
            is KtProperty -> if (element.parent is KtClassBody) continue else return element
            is KtFunction -> if (element.hasModifier(KtTokens.OVERRIDE_KEYWORD)) continue else return element
            is KtEnumEntry -> continue
            is KtDeclaration -> return element
            else -> continue
        }

    }
    return null
}

@OptIn(KaExperimentalApi::class)
private fun KaSession.areTypeArgumentsEqual(
    originalCallExpression: KtCallExpression,
    newCallExpression: KtCallExpression,
    approximateFlexible: Boolean,
): Boolean {
    val originalTypeArgs = originalCallExpression
        .resolveToCall()
        ?.singleFunctionCallOrNull()
        ?.typeArgumentsMapping
        ?: return false

    val newTypeArgs = newCallExpression
        .resolveToCall()
        ?.singleFunctionCallOrNull()
        ?.typeArgumentsMapping
        ?: return false

    val oldDiagnostics = originalCallExpression.diagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)
    val newDiagnostics = newCallExpression.containingKtFile.collectDiagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)

    return areAllTypesEqual(originalTypeArgs, newTypeArgs, approximateFlexible) &&
            !hasNewDiagnostics(oldDiagnostics, newDiagnostics)
}

private fun KaSession.areAllTypesEqual(
    originalTypeArgs: Map<KaTypeParameterSymbol, KaType>,
    newTypeArgs: Map<KaTypeParameterSymbol, KaType>,
    approximateFlexible: Boolean,
): Boolean {
    return originalTypeArgs.size == newTypeArgs.size &&
            originalTypeArgs.values.zip(newTypeArgs.values).all { (originalType, newType) ->
                areTypesEqual(originalType, newType, approximateFlexible)
            }
}

private fun hasNewDiagnostics(
    oldDiagnostics: Collection<KaDiagnosticWithPsi<*>>,
    newDiagnostics: Collection<KaDiagnosticWithPsi<*>>,
): Boolean = (newDiagnostics - oldDiagnostics).any {
    it is KaFirDiagnostic.UnresolvedReference ||
            it is KaFirDiagnostic.BuilderInferenceStubReceiver ||
            it is KaFirDiagnostic.ImplicitNothingReturnType
}

private fun KaSession.areTypesEqual(
    type1: KaType,
    type2: KaType,
    approximateFlexible: Boolean,
): Boolean {
    return if (type1 is KaTypeParameterType && type2 is KaTypeParameterType) {
        type1.name == type2.name
    } else if (type1 is KaDefinitelyNotNullType && type2 is KaDefinitelyNotNullType) {
        areTypesEqual(type1.original, type2.original, approximateFlexible)
    } else {
        (approximateFlexible || type1.hasFlexibleNullability == type2.hasFlexibleNullability) &&
                type1.semanticallyEquals(type2)
    }
}
