// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.pullUp

import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty

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
