// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.pushDown

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.analysis.api.types.KaSubstitutor
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.k2.refactoring.pullUp.computeAndRenderReturnType
import org.jetbrains.kotlin.idea.k2.refactoring.pullUp.makeAbstract
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KtPsiClassWrapper
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

@OptIn(KaExperimentalApi::class)
internal fun KaSession.createRemoveOriginalMemberAction(
    sourceClass: KtClass,
    memberInfo: KotlinMemberInfo,
    substitutor: KaSubstitutor,
): RemovalAction? = when (memberInfo.member) {
    is KtProperty, is KtNamedFunction -> createRemoveCallableMemberAction(memberInfo, sourceClass, substitutor)
    is KtClassOrObject, is KtPsiClassWrapper -> createRemoveClassLikeMemberAction(memberInfo, sourceClass)
    else -> null
}

@OptIn(KaExperimentalApi::class)
private fun KaSession.createRemoveCallableMemberAction(
    memberInfo: KotlinMemberInfo,
    sourceClass: KtClass,
    substitutor: KaSubstitutor,
): RemovalAction? {
    val member = memberInfo.member as KtCallableDeclaration
    val memberSymbol = member.symbol as? KaCallableSymbol ?: return null

    if (memberSymbol.modality != KaSymbolModality.ABSTRACT && memberInfo.isToAbstract) {
        val renderedType = computeAndRenderReturnType(memberSymbol, member, substitutor)

        return RemovalAction {
            if (member.hasModifier(KtTokens.PRIVATE_KEYWORD)) {
                member.addModifier(KtTokens.PROTECTED_KEYWORD)
            }
            if (renderedType != null) {
                member.typeReference = KtPsiFactory(member.project).createType(renderedType)
            }
            makeAbstract(member, sourceClass)
            member.typeReference?.let { typeReference ->
                shortenReferences(typeReference)
            }
        }
    }

    return RemovalAction { member.delete() }
}

private fun KaSession.createRemoveClassLikeMemberAction(
    memberInfo: KotlinMemberInfo,
    sourceClass: KtClass,
): RemovalAction? {
    val member = memberInfo.member
    return if (memberInfo.overrides != null) {
        val superTypeListEntry = getSuperTypeEntryBySymbol(
            sourceClass,
            member.symbol as KaClassSymbol,
        ) ?: return null

        RemovalAction { sourceClass.removeSuperTypeListEntry(superTypeListEntry) }
    } else {
        RemovalAction { member.delete() }
    }
}
