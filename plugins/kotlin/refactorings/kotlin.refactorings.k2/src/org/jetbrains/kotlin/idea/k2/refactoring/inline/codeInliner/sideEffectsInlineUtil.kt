// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.inline.codeInliner

import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtStringTemplateEntryWithExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtSuperExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtUnaryExpression
import org.jetbrains.kotlin.psi.psiUtil.isNull
import kotlin.collections.contains

internal fun KtExpression?.shouldKeepValue(usageCount: Int): Boolean {
    if (usageCount == 1) return false
    val sideEffectOnly = usageCount == 0

    return when (this) {
        is KtCallExpression -> getCopyableUserData(InlineDataKeys.SIDE_EFFECTS) ?: true
        is KtSimpleNameExpression -> false
        is KtQualifiedExpression -> receiverExpression.shouldKeepValue(usageCount) || selectorExpression.shouldKeepValue(usageCount)
        is KtUnaryExpression -> operationToken in setOf(KtTokens.PLUSPLUS, KtTokens.MINUSMINUS) ||
                getCopyableUserData(InlineDataKeys.SIDE_EFFECTS) == true ||
                baseExpression.shouldKeepValue(usageCount)

        is KtStringTemplateExpression -> entries.any {
            if (sideEffectOnly) it.expression.shouldKeepValue(usageCount) else it is KtStringTemplateEntryWithExpression
        }

        is KtLambdaExpression -> !sideEffectOnly
        is KtThisExpression, is KtSuperExpression, is KtConstantExpression -> false
        is KtParenthesizedExpression -> expression.shouldKeepValue(usageCount)
        is KtArrayAccessExpression -> !sideEffectOnly ||
                getCopyableUserData(InlineDataKeys.SIDE_EFFECTS) ?: false ||
                arrayExpression.shouldKeepValue(usageCount) ||
                indexExpressions.any { it.shouldKeepValue(usageCount) }

        is KtBinaryExpression -> !sideEffectOnly ||
                operationToken == KtTokens.IDENTIFIER ||
                getCopyableUserData(InlineDataKeys.SIDE_EFFECTS) == true ||
                left.shouldKeepValue(usageCount) ||
                right.shouldKeepValue(usageCount)

        is KtIfExpression -> !sideEffectOnly ||
                condition.shouldKeepValue(usageCount) ||
                then.shouldKeepValue(usageCount) ||
                `else`.shouldKeepValue(usageCount)

        is KtBinaryExpressionWithTypeRHS -> !(sideEffectOnly && left.isNull())
        is KtClassLiteralExpression -> false
        is KtCallableReferenceExpression -> false
        null -> false
        else -> true
    }
}