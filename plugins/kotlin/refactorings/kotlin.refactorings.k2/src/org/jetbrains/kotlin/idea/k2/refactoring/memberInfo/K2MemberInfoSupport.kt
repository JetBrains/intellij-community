// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.memberInfo

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.renderer.base.KaKeywordsRenderer
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.KaRendererAnnotationsFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KaDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.callables.KaPropertyAccessorsRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.superTypes.KaSuperTypesFilter
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfoSupport
import org.jetbrains.kotlin.psi.KtNamedDeclaration

class K2MemberInfoSupport : KotlinMemberInfoSupport {
    @KaExperimentalApi
    private val renderer = KaDeclarationRendererForSource.WITH_SHORT_NAMES.with {
        annotationRenderer = annotationRenderer.with {
            keywordsRenderer = KaKeywordsRenderer.NONE
            annotationFilter = KaRendererAnnotationsFilter.NONE
            superTypesFilter = KaSuperTypesFilter.NONE
            propertyAccessorsRenderer = KaPropertyAccessorsRenderer.NONE
        }
    }

    override fun getOverrides(member: KtNamedDeclaration): Boolean? {
        analyze(member) {
            val memberSymbol = (member.symbol as? KaCallableSymbol) ?: return null
            val allOverriddenSymbols = memberSymbol.allOverriddenSymbols.toList()
            if (allOverriddenSymbols.isNotEmpty()) return allOverriddenSymbols.any { it.modality != KaSymbolModality.ABSTRACT }
            return null
        }
    }

    @OptIn(KaExperimentalApi::class)
    override fun renderMemberInfo(member: KtNamedDeclaration): String {
        analyze(member) {
            val memberSymbol = member.symbol
            return memberSymbol.render(renderer)
        }
    }
}