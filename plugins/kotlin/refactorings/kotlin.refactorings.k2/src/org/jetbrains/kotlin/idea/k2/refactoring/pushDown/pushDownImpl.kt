// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.pushDown

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaSubstitutor
import org.jetbrains.kotlin.idea.k2.refactoring.findCallableMemberBySignature
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KtPsiClassWrapper
import org.jetbrains.kotlin.idea.refactoring.pullUp.addMemberToTarget
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.types.Variance
import java.util.concurrent.Callable

internal data class MemberContext(
    val member: KtCallableDeclaration,
    val modality: KaSymbolModality,
    val doesNotOverride: Boolean,
)

@OptIn(KaExperimentalApi::class)
internal fun KaSession.createPushDownAction(
    sourceClass: KtClass,
    memberInfo: KotlinMemberInfo,
    targetClass: KtClassOrObject,
    substitutor: KaSubstitutor,
): Callable<KtNamedDeclaration>? = when (memberInfo.member) {
    is KtProperty, is KtNamedFunction ->
        createPushDownActionForCallableMember(
            memberInfo,
            targetClass,
            substitutor,
        )

    is KtClassOrObject, is KtPsiClassWrapper -> {
        createPushDownActionForClassLikeMember(
            memberInfo,
            sourceClass,
            targetClass,
            substitutor,
        )
    }

    else -> null
}

internal fun KaSession.findSuperTypeEntryForSymbol(
    sourceClass: KtClassOrObject,
    symbol: KaClassSymbol,
): KtSuperTypeListEntry? = sourceClass.superTypeListEntries.firstOrNull {
    val referencedType = it.typeReference?.type
    referencedType?.expandedSymbol == symbol
}

@OptIn(KaExperimentalApi::class)
private fun KaSession.createPushDownActionForCallableMember(
    memberInfo: KotlinMemberInfo,
    targetClass: KtClassOrObject,
    substitutor: KaSubstitutor,
): Callable<KtNamedDeclaration>? {
    val targetClassSymbol = targetClass.symbol as KaClassSymbol
    val member = memberInfo.member as KtCallableDeclaration
    val memberSymbol = member.symbol
    val substituted = (memberSymbol as KaCallableSymbol).substitute(substitutor)
    val targetMemberSymbol = targetClassSymbol.findCallableMemberBySignature(substituted, ignoreReturnType = true)
    val targetMember = targetMemberSymbol?.psi as? KtCallableDeclaration

    val sourceClassSymbol = memberSymbol.containingDeclaration as KaClassSymbol
    val sourceClassKind = sourceClassSymbol.classKind
    val targetClassKind = targetClassSymbol.classKind

    val memberContext = MemberContext(
        targetMember ?: member,
        memberSymbol.modality,
        memberSymbol.allOverriddenSymbols.none(),
    )

    return Callable<KtNamedDeclaration> {
        if (targetMember != null) {
            updateExistingCallableMember(
                memberContext,
                memberInfo.isToAbstract,
            )
        } else {
            addCallableMember(
                memberContext,
                sourceClassKind,
                targetClass,
                targetClassKind,
                memberInfo.isToAbstract,
            )
        }
    }
}

private fun updateExistingCallableMember(
    memberContext: MemberContext,
    makeAbstract: Boolean,
): KtCallableDeclaration = memberContext.member.apply {
    if (memberContext.modality != KaSymbolModality.ABSTRACT && makeAbstract) {
        addModifier(KtTokens.OVERRIDE_KEYWORD)
    } else {
        if (memberContext.doesNotOverride) {
            removeModifier(KtTokens.OVERRIDE_KEYWORD)
        } else {
            addModifier(KtTokens.OVERRIDE_KEYWORD)
        }
    }
}

private fun addCallableMember(
    memberContext: MemberContext,
    sourceClassKind: KaClassKind?,
    targetClass: KtClassOrObject,
    targetClassKind: KaClassKind,
    makeAbstract: Boolean,
): KtCallableDeclaration = addMemberToTarget(memberContext.member, targetClass).apply {
    if (sourceClassKind == KaClassKind.INTERFACE &&
        targetClassKind != KaClassKind.INTERFACE &&
        memberContext.modality == KaSymbolModality.ABSTRACT
    ) {
        addModifier(KtTokens.ABSTRACT_KEYWORD)
    }

    if (memberContext.modality != KaSymbolModality.ABSTRACT && makeAbstract) {
        KtTokens.VISIBILITY_MODIFIERS.types.forEach { removeModifier(it as KtModifierKeywordToken) }
        addModifier(KtTokens.OVERRIDE_KEYWORD)
    }
} as KtCallableDeclaration

@OptIn(KaExperimentalApi::class)
private fun KaSession.createPushDownActionForClassLikeMember(
    memberInfo: KotlinMemberInfo,
    sourceClass: KtClassOrObject,
    targetClass: KtClassOrObject,
    substitutor: KaSubstitutor,
): Callable<KtNamedDeclaration>? {
    return if (memberInfo.overrides != null) {
        val superTypeListEntry = findSuperTypeEntryForSymbol(
            sourceClass,
            memberInfo.member.symbol as KaClassSymbol,
        ) ?: return null

        val referencedType = superTypeListEntry.typeReference?.type!!
        val referencedClass = referencedType.expandedSymbol ?: return null

        val targetClassSymbol = targetClass.symbol as KaClassSymbol
        if (targetClass.symbol == referencedClass || targetClassSymbol.isDirectSubClassOf(referencedClass)) return null

        val typeInTargetClass = substitutor.substitute(referencedType)
        if (typeInTargetClass is KaErrorType) return null

        val renderedType = typeInTargetClass.render(position = Variance.INVARIANT)
        val newSpecifier = KtPsiFactory(targetClass.project).createSuperTypeEntry(renderedType)
        Callable<KtNamedDeclaration> {
            targetClass.addSuperTypeListEntry(newSpecifier)
            null
        }
    } else {
        Callable<KtNamedDeclaration> {
            addMemberToTarget(memberInfo.member, targetClass)
        }
    }
}
