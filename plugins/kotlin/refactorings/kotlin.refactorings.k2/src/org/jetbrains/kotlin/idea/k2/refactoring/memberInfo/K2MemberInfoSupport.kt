// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.memberInfo

import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.renderer.base.KtKeywordsRenderer
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.KaRendererAnnotationsFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KtDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.callables.KaPropertyAccessorsRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.superTypes.KaSuperTypesFilter
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaSymbolWithModality
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfoSupport
import org.jetbrains.kotlin.psi.KtNamedDeclaration

class K2MemberInfoSupport : KotlinMemberInfoSupport {
    private val renderer = KtDeclarationRendererForSource.WITH_SHORT_NAMES.with {
        annotationRenderer = annotationRenderer.with {
            keywordsRenderer = KtKeywordsRenderer.NONE
            annotationFilter = KaRendererAnnotationsFilter.NONE
            superTypesFilter = KaSuperTypesFilter.NONE
            propertyAccessorsRenderer = KaPropertyAccessorsRenderer.NONE
        }
    }

    override fun getOverrides(member: KtNamedDeclaration): Boolean? {
        analyze(member) {
            val memberSymbol = (member.getSymbol() as? KaCallableSymbol) ?: return null
            val allOverriddenSymbols = memberSymbol.getAllOverriddenSymbols().filterIsInstance<KaSymbolWithModality>()
            if (allOverriddenSymbols.isNotEmpty()) return allOverriddenSymbols.any { it.modality != Modality.ABSTRACT }
            return null
        }
    }

    override fun renderMemberInfo(member: KtNamedDeclaration): String {
        analyze(member) {
            val memberSymbol = member.getSymbol()
            return memberSymbol.render(renderer)
        }
    }
}