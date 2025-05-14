// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.pullUp

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.renderer.declarations.bodies.KaRendererBodyMemberScopeProvider
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KaDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.KaDeclarationModifiersRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.renderers.KaModifierListRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.callables.KaPropertyAccessorsRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.callables.KaValueParameterSymbolRenderer
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.utils.printer.PrettyPrinter
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle

@Nls
@OptIn(KaExperimentalApi::class)
fun KaDeclarationSymbol.renderForConflicts(analysisSession: KaSession): String = with(analysisSession) {
    when (this@renderForConflicts) {
        is KaClassSymbol -> {
            @NlsSafe val text = "${getClassKindPrefix(this@renderForConflicts)} " + this@renderForConflicts.name
            text
        }

        is KaFunctionSymbol -> {
            KotlinBundle.message("text.function.in.ticks.0", render(CallableRenderer))
        }

        is KaPropertySymbol -> {
            KotlinBundle.message("text.property.in.ticks.0", render(CallableRenderer))
        }

        is KaPackageSymbol -> {
            @NlsSafe val text = fqName.asString()
            text
        }

        else -> {
            ""
        }
    }
}

@KaExperimentalApi
private val NoModifierListRenderer = object : KaModifierListRenderer {
    override fun renderModifiers(
        analysisSession: KaSession,
        symbol: KaDeclarationSymbol,
        declarationModifiersRenderer: KaDeclarationModifiersRenderer,
        printer: PrettyPrinter
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


private fun getClassKindPrefix(symbol: KaClassSymbol): String = when (symbol.classKind) {
    KaClassKind.CLASS -> "class"
    KaClassKind.ENUM_CLASS -> "enum class"
    KaClassKind.ANNOTATION_CLASS -> "annotation class"
    KaClassKind.OBJECT -> "object"
    KaClassKind.COMPANION_OBJECT -> "companion object"
    KaClassKind.INTERFACE -> "interface"
    KaClassKind.ANONYMOUS_OBJECT -> "anonymous object"
}
