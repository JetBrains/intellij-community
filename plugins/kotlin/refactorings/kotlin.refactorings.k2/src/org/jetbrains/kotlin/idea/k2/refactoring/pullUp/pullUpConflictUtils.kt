// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.pullUp

import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.renderer.declarations.bodies.KaRendererBodyMemberScopeProvider
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KaDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.KaDeclarationModifiersRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.renderers.KaModifierListRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.callables.KaPropertyAccessorsRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.callables.KaValueParameterSymbolRenderer
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.utils.printer.PrettyPrinter
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.renderForConflict

@Nls
@OptIn(KaExperimentalApi::class)
internal fun KaDeclarationSymbol.renderForConflicts(
    analysisSession: KaSession,
): String = with(analysisSession) {
    renderForConflict(CallableRenderer)
}

@KaExperimentalApi
private val NoModifierListRenderer = object : KaModifierListRenderer {
    override fun renderModifiers(
        analysisSession: KaSession,
        symbol: KaDeclarationSymbol,
        declarationModifiersRenderer: KaDeclarationModifiersRenderer,
        printer: PrettyPrinter,
    ): Unit = Unit
}

@OptIn(KaExperimentalApi::class)
private val CallableRenderer = KaDeclarationRendererForSource.WITH_SHORT_NAMES.with {
    valueParameterRenderer = KaValueParameterSymbolRenderer.TYPE_ONLY
    modifiersRenderer = modifiersRenderer.with {
        modifierListRenderer = NoModifierListRenderer
    }
    propertyAccessorsRenderer = KaPropertyAccessorsRenderer.NONE
    bodyMemberScopeProvider = KaRendererBodyMemberScopeProvider.NONE
}
