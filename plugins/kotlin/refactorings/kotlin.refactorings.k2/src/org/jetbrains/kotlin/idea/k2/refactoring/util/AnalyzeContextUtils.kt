// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.refactoring.util

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

/**
 * Finds the nearest enclosing declaration that can be used as a context for a code fragment built from [this] expression.
 */
@ApiStatus.Internal
@Deprecated(
    message = "Temporary workaround. Do not use.",
    level = DeprecationLevel.WARNING,
)
@ApiStatus.ScheduledForRemoval
fun KtExpression.findContextToAnalyze(): KtExpression? {
    for (element in parentsWithSelf) {
        when (element) {
            is KtFunctionLiteral -> continue
            is KtParameter -> continue
            is KtPropertyAccessor -> continue
            is KtProperty -> if (element.isLocal) continue else return element
            is KtFunction -> if (element.hasModifier(KtTokens.OVERRIDE_KEYWORD)) continue else return element
            is KtDeclaration -> return element
            else -> continue
        }
    }
    return null
}
