// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.memberInfo

import org.jetbrains.kotlin.analysis.api.rendering.KaPiece
import org.jetbrains.kotlin.analysis.api.rendering.KaRenderer
import org.jetbrains.kotlin.analysis.api.rendering.pushEmpty
import org.jetbrains.kotlin.analysis.api.rendering.renderToString
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.analysis.api.symbols.allOverriddenSymbols
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfoSupport
import org.jetbrains.kotlin.idea.util.withShortNames
import org.jetbrains.kotlin.psi.KtNamedDeclaration

class K2MemberInfoSupport : KotlinMemberInfoSupport {
    private val renderer: KaRenderer = KaRenderer.withShortNames.copy {
        pushEmpty(KaPiece.Annotations)
        pushEmpty(KaPiece.SupertypeList)
        pushEmpty(KaPiece.ClassBody)
        pushEmpty(KaPiece.PropertyInitializer)
        pushEmpty(KaPiece.PropertyAccessors)
    }

    override fun getOverrides(member: KtNamedDeclaration): Boolean? {
        analyze(member) {
            val memberSymbol = (member.symbol as? KaCallableSymbol) ?: return null
            val allOverriddenSymbols = memberSymbol.allOverriddenSymbols.toList()
            if (allOverriddenSymbols.isNotEmpty()) return allOverriddenSymbols.any { it.modality != KaSymbolModality.ABSTRACT }
            return null
        }
    }

    override fun renderMemberInfo(member: KtNamedDeclaration): String {
        analyze(member) {
            val memberSymbol = member.symbol
            return renderer.renderToString(memberSymbol)
        }
    }
}