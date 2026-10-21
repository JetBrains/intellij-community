// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.navigation

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.renderer.render
import org.jetbrains.kotlin.analysis.api.renderer.types.KaExpandedTypeRenderingMode
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeParameterListOwner
import org.jetbrains.kotlin.psi.psiUtil.isExtensionDeclaration
import org.jetbrains.kotlin.types.Variance

/**
 * Chooses the declaration among [candidates] that corresponds to [original], for example a source declaration for a compiled one.
 *
 * The PSI shape decides first. When several candidates have the same shape, the rendered receiver and parameter types decide.
 * Returns null for a declaration that is not a constructor, a function, or a property.
 */
@ApiStatus.Internal
fun chooseCorrespondingCallable(original: KtCallableDeclaration, candidates: List<KtDeclaration>): KtCallableDeclaration? =
    when (original) {
        is KtConstructor<*> -> chooseCallableCandidate(original, candidates, ::constructorsMatchByPsi)
        is KtNamedFunction -> chooseCallableCandidate(original, candidates, ::functionsMatchByPsi)
        is KtProperty -> chooseCallableCandidate(original, candidates, ::propertiesMatchByPsi)
        else -> null
    }

private inline fun <reified C : KtCallableDeclaration> chooseCallableCandidate(
    original: C,
    candidates: List<KtDeclaration>,
    matchesByPsi: (C, C) -> Boolean,
): C? {
    val filteredCandidates = candidates.filterIsInstance<C>().filter { matchesByPsi(original, it) }
    filteredCandidates.singleOrNull()?.let { return it }

    return filteredCandidates.firstOrNull { compareCallableTypesByResolve(original, it) }
        ?: filteredCandidates.firstOrNull()
}

private fun propertiesMatchByPsi(firstProperty: KtProperty, secondProperty: KtProperty): Boolean {
    if (firstProperty.name != secondProperty.name) return false
    if (firstProperty.isExtensionDeclaration() != secondProperty.isExtensionDeclaration()) return false

    if (!typeParameterMatches(firstProperty, secondProperty)) return false

    return true
}

private fun functionsMatchByPsi(first: KtNamedFunction, second: KtNamedFunction): Boolean {
    if (first.name != second.name) return false
    if (first.isExtensionDeclaration() != second.isExtensionDeclaration()) return false

    if (!valueParameterMatches(first, second)) return false
    if (!typeParameterMatches(first, second)) return false

    return true
}

private fun constructorsMatchByPsi(first: KtConstructor<*>, second: KtConstructor<*>): Boolean {
    if (first is KtPrimaryConstructor != second is KtPrimaryConstructor) return false
    if (!valueParameterMatches(first, second)) return false

    return true
}

private fun valueParameterMatches(
    firstValueParamOwner: KtCallableDeclaration,
    secondValueParameterOwner: KtCallableDeclaration
): Boolean {
    val firstValueParameters = firstValueParamOwner.valueParameters
    val secondValueParameters = secondValueParameterOwner.valueParameters

    if (firstValueParameters.size != secondValueParameters.size) return false
    for (i in firstValueParameters.indices) {
        val first = firstValueParameters[i]
        val second = secondValueParameters[i]
        if (first.name != second.name) return false
        if (first.isVarArg != second.isVarArg) return false
    }
    return true
}

private fun typeParameterMatches(
    firstTypeParamOwner: KtTypeParameterListOwner,
    secondTypeParamOwner: KtTypeParameterListOwner
): Boolean {
    val firstTypeParameters = firstTypeParamOwner.typeParameters
    val secondTypeParameters = secondTypeParamOwner.typeParameters
    if (firstTypeParameters.size != secondTypeParameters.size) return false
    for (i in firstTypeParameters.indices) {
        val first = firstTypeParameters[i]
        val second = secondTypeParameters[i]
        if (first.name != second.name) return false
    }
    return true
}

private fun compareCallableTypesByResolve(first: KtCallableDeclaration, second: KtCallableDeclaration): Boolean {
    // symbols should be rendered from corresponding sessions
    val firstRendered = renderTypesForComparison(first)
    val secondRendered = renderTypesForComparison(second)
    return firstRendered == secondRendered
}

// Maybe called from EDT by IJ Platform :(
@OptIn(KaAllowAnalysisOnEdt::class)
private fun renderTypesForComparison(declaration: KtCallableDeclaration) = allowAnalysisOnEdt {
    @OptIn(KaAllowAnalysisFromWriteAction::class)
    allowAnalysisFromWriteAction {
        analyze(declaration) {
            buildString {
                val symbol = declaration.symbol as KaCallableSymbol
                symbol.receiverType?.let { receiverType ->
                    append(receiverType.render(renderer, position = Variance.INVARIANT))
                    append('.')
                }
                if (symbol is KaFunctionSymbol) {
                    symbol.valueParameters.joinTo(this) { it.returnType.render(renderer, position = Variance.INVARIANT) }
                }
            }
        }
    }
}

@KaExperimentalApi
private val renderer = KaTypeRendererForSource.WITH_QUALIFIED_NAMES.with {
    expandedTypeRenderingMode = KaExpandedTypeRenderingMode.RENDER_EXPANDED_TYPE
}
