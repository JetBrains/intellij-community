// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.pullUp

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaSubstitutor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.types.Variance

@OptIn(KaExperimentalApi::class)
internal fun KaSession.createSuperTypeEntryForAddition(
    delegator: KtSuperTypeListEntry,
    targetClass: KtClassOrObject,
    substitutor: KaSubstitutor,
): KtSuperTypeListEntry? {
    val referencedType = delegator.typeReference?.type ?: return null
    val referencedClass = referencedType.expandedSymbol ?: return null

    val targetClassSymbol = targetClass.symbol as? KaClassSymbol ?: return null
    if (targetClassSymbol == referencedClass || targetClassSymbol.isDirectSubClassOf(referencedClass)) return null

    val typeInTargetClass = substitutor.substitute(referencedType)
    if (typeInTargetClass is KaErrorType) return null

    val renderedType = typeInTargetClass.render(position = Variance.INVARIANT)
    return KtPsiFactory(targetClass.project).createSuperTypeEntry(renderedType)
}

@OptIn(KaExperimentalApi::class)
internal fun KaSession.computeAndRenderReturnType(
    originalCallableSymbol: KaCallableSymbol,
    copiedDeclaration: KtCallableDeclaration,
    substitutor: KaSubstitutor,
): String? {
    if (copiedDeclaration.typeReference != null) return null

    var returnType = originalCallableSymbol.returnType
    if (returnType is KaErrorType) {
        returnType = builtinTypes.nullableAny
    } else {
        returnType = substitutor.substitute(returnType)
    }
    return if (copiedDeclaration is KtProperty || !returnType.isUnitType) {
        returnType.render(position = Variance.INVARIANT)
    } else {
        null
    }
}

internal fun makeAbstract(
    member: KtCallableDeclaration,
    targetClass: KtClass,
) {
    if (!targetClass.isInterface()) {
        member.addModifier(KtTokens.ABSTRACT_KEYWORD)
    }

    val deleteFrom = when (member) {
        is KtProperty -> {
            member.equalsToken ?: member.delegate ?: member.accessors.firstOrNull()
        }

        is KtNamedFunction -> {
            member.equalsToken ?: member.bodyExpression
        }

        else -> null
    }

    if (deleteFrom != null) {
        member.deleteChildRange(deleteFrom, member.lastChild)
    }
}
