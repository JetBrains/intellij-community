// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.util

import com.intellij.psi.util.isAncestor
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.startOffset
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.components.collectDiagnostics
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.analysis.api.types.KaDefinitelyNotNullType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.end
import org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.range
import org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.start
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.KtTypeProjection
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

private val INLINE_REIFIED_FUNCTIONS_WITH_INSIGNIFICANT_TYPE_ARGUMENTS: Set<String> = setOf("kotlin.arrayOf")

fun KaSession.areTypeArgumentsRedundant(
    typeArgumentList: KtTypeArgumentList,
    approximateFlexible: Boolean = false,
): Boolean {
    val callExpression = typeArgumentList.parent as? KtCallExpression ?: return false
    val symbol = callExpression.resolveToCall()?.successfulFunctionCallOrNull()?.symbol ?: return false
    if (isInlineReifiedFunction(symbol)) return false

    if (symbol.receiverType == null && callExpression.valueArguments.isEmpty()) {
        // no reasons to check cases like `val list = emptyList<T>()`
        val parent = callExpression.parent
        val property =
            // `val list = emptyList<T>()`
            parent as? KtProperty
            // `val list = emptyList<T>().smth{...}`
                ?: (parent as? KtDotQualifiedExpression)?.parent as? KtProperty
        if (property != null && property.typeReference == null) {
            return false
        }
    }

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

private fun KaSession.isInlineReifiedFunction(symbol: KaFunctionSymbol): Boolean =
    symbol is KaNamedFunctionSymbol &&
            symbol.importableFqName?.asString() !in INLINE_REIFIED_FUNCTIONS_WITH_INSIGNIFICANT_TYPE_ARGUMENTS &&
            symbol.isInline &&
            symbol.typeParameters.any { it.isReified }

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

private fun areTypeArgumentsEqual(
    originalCallExpression: KtCallExpression,
    newCallExpression: KtCallExpression,
    approximateFlexible: Boolean,
): Boolean = analyze(newCallExpression) {
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

    return areAllTypesEqual(originalTypeArgs, newTypeArgs, approximateFlexible) &&
            !hasNewDiagnostics(originalCallExpression, newCallExpression)
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

context(_: KaSession)
private fun hasNewDiagnostics(originalCallExpression: KtCallExpression, newCallExpression: KtCallExpression): Boolean {
    val newDiagnostics = newCallExpression.nestedDiagnostics
    if (newDiagnostics.isEmpty()) return false

    val oldDiagnostics = originalCallExpression.nestedDiagnostics

    // Diagnostics cannot be compared directly since they have only identity equals/hashCode
    // Also, original call expression and new call expression files have a different set of psi instances since
    // they effectively in different files
    return newDiagnostics.size != oldDiagnostics.size
}

// TODO: when KT-63221 is fixed use `diagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)` to reduce resolve and avoid psi checks
@OptIn(KaExperimentalApi::class)
context(_: KaSession)
private val KtCallExpression.nestedDiagnostics: List<KaDiagnosticWithPsi<*>>
    get() = containingKtFile
        .collectDiagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)
        .filter { diagnostic ->
            when (diagnostic) {
                is KaFirDiagnostic.UnresolvedReference,
                is KaFirDiagnostic.BuilderInferenceStubReceiver,
                is KaFirDiagnostic.ImplicitNothingReturnType,
                    -> isAncestor(diagnostic.psi, strict = false)

                else -> false
            }
        }

private fun KaSession.areTypesEqual(
    type1: KaType,
    type2: KaType,
    approximateFlexible: Boolean,
): Boolean = when (type1) {
    is KaTypeParameterType if type2 is KaTypeParameterType -> {
        type1.name == type2.name
    }

    is KaDefinitelyNotNullType if type2 is KaDefinitelyNotNullType -> {
        areTypesEqual(type1.original, type2.original, approximateFlexible)
    }

    else -> (approximateFlexible || type1.hasFlexibleNullability == type2.hasFlexibleNullability) &&
            type1.semanticallyEquals(type2)
}

context(_: KaSession)
fun KtTypeProjection.canBeReplacedWithUnderscore(callExpression: KtCallExpression): Boolean {
    val newCallExpression = buildCallExpressionWithUnderscores(callExpression, this)
        ?: return false

    return areTypeArgumentsEqual(callExpression, newCallExpression, false)
}

private fun buildCallExpressionWithUnderscores(element: KtCallExpression, typeProjectionToReplace: KtTypeProjection): KtCallExpression? {
    val context = findContextToAnalyze(element) ?: return null
    val typeArgumentRange = typeProjectionToReplace.textRange.shiftLeft(context.startOffset)

    val textWithUnderscore = context.text.replaceRange(typeArgumentRange.startOffset, typeArgumentRange.endOffset, "_")

    val (prefix, suffix) = if (context.parent !is KtClassBody) {
        "object Obj {" to "}"
    } else "" to ""

    val codeFragment = KtPsiFactory(
        element.project,
        markGenerated = false,
    ).createBlockCodeFragment("$prefix$textWithUnderscore$suffix", context)

    return codeFragment.findElementAt(typeArgumentRange.start + prefix.length)?.parentOfType()
}