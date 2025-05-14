// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.pushDown

import kotlinx.coroutines.Runnable
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaSubstitutor
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.k2.refactoring.pullUp.makeAbstract
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KtPsiClassWrapper
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.types.Variance

@OptIn(KaExperimentalApi::class)
internal fun KaSession.createRemoveOriginalMemberAction(
    sourceClass: KtClass,
    memberInfo: KotlinMemberInfo,
    substitutor: KaSubstitutor,
): Runnable? = when (memberInfo.member) {
    is KtProperty, is KtNamedFunction -> createRemoveCallableMemberAction(memberInfo, sourceClass, substitutor)
    is KtClassOrObject, is KtPsiClassWrapper -> createRemoveClassLikeMemberAction(memberInfo, sourceClass)
    else -> null
}

@OptIn(KaExperimentalApi::class)
private fun KaSession.createRemoveCallableMemberAction(
    memberInfo: KotlinMemberInfo,
    sourceClass: KtClass,
    substitutor: KaSubstitutor,
): Runnable? {
    val member = memberInfo.member as KtCallableDeclaration
    val memberSymbol = member.symbol

    if (memberSymbol.modality != KaSymbolModality.ABSTRACT && memberInfo.isToAbstract) {
        val (needToSetType, type) = if (member.typeReference == null) {
            var type = (memberSymbol as KaCallableSymbol).returnType
            if (type is KaErrorType) {
                type = builtinTypes.nullableAny
            } else {
                type = substitutor.substitute(type)
            }
            (member is KtProperty || !type.isUnitType) to type
        } else {
            false to null
        }

        val renderedType = type?.render(position = Variance.INVARIANT)

        return Runnable {
            if (member.hasModifier(KtTokens.PRIVATE_KEYWORD)) {
                member.addModifier(KtTokens.PROTECTED_KEYWORD)
            }
            if (needToSetType) {
                member.typeReference = KtPsiFactory(member.project).createType(renderedType!!)
                shortenReferences(member.typeReference!!)
            }
            makeAbstract(member, sourceClass)
            member.typeReference
        }
    }

    return Runnable { member.delete() }
}

private fun KaSession.createRemoveClassLikeMemberAction(
    memberInfo: KotlinMemberInfo,
    sourceClass: KtClass,
): Runnable? {
    val member = memberInfo.member
    return if (memberInfo.overrides != null) {
        val superTypeListEntry = findSuperTypeEntryForSymbol(
            sourceClass,
            member.symbol as KaClassSymbol,
        ) ?: return null

        Runnable { sourceClass.removeSuperTypeListEntry(superTypeListEntry) }
    } else {
        Runnable { member.delete() }
    }
}
