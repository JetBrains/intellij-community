// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.memberInfo

import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithModality
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfoSupport
import org.jetbrains.kotlin.psi.KtNamedDeclaration

class K2MemberInfoSupport : KotlinMemberInfoSupport {
    override fun getOverrides(member: KtNamedDeclaration): Boolean? {
        analyze(member) {
            val memberSymbol = (member.getSymbol() as? KtCallableSymbol) ?: return null
            val allOverriddenSymbols = memberSymbol.getAllOverriddenSymbols().filterIsInstance<KtSymbolWithModality>()
            if (allOverriddenSymbols.isNotEmpty()) return allOverriddenSymbols.any { it.modality != Modality.ABSTRACT }
            return null
        }
    }
}