// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.injection

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.ElementManipulators
import com.intellij.psi.impl.LanguageConstantExpressionEvaluator
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.resolution.function
import org.jetbrains.kotlin.analysis.api.resolution.single
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.resolution.tryResolveCall
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.idea.base.injection.IndentHandler
import org.jetbrains.kotlin.idea.base.injection.TrimIndentHandler
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name.identifier
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

private val trimIndentCallableId = CallableId(StandardClassIds.BASE_TEXT_PACKAGE, identifier("trimIndent"))
private val trimMarginCallableId = CallableId(StandardClassIds.BASE_TEXT_PACKAGE, identifier("trimMargin"))

/**
 * Returns the [IndentHandler] of a `trimIndent()` or a `trimMargin()` call, or `null` for another call.
 */
@OptIn(KaAllowAnalysisOnEdt::class, KaAllowAnalysisFromWriteAction::class)
internal fun KtCallExpression.findTrimIndentHandler(): IndentHandler? {
    val callableId = allowAnalysisOnEdt {
        allowAnalysisFromWriteAction {
            analyze(this) {
                tryResolveCall()?.single?.function?.symbol?.callableId
            }
        }
    }
    return when (callableId) {
        trimIndentCallableId -> TrimIndentHandler()
        trimMarginCallableId -> {
            val argumentExpression = valueArguments.firstOrNull()?.getArgumentExpression()
            TrimIndentHandler(argumentExpression.computeConstantExpression() as? String ?: "|")
        }

        else -> null
    }
}

/**
 * Returns the ranges of the value of this literal that remain after the `trimIndent()` or the `trimMargin()` call on it.
 * The ranges are relative to the literal.
 * Returns `null` when this literal is not the receiver of such a call.
 */
@ApiStatus.Internal
fun KtStringTemplateExpression.getTrimmedValueRanges(): List<TextRange>? {
    if (!Registry.`is`("kotlin.injection.handle.trimindent", true)) return null
    val dotQualifiedExpression = parent as? KtDotQualifiedExpression ?: return null
    if (dotQualifiedExpression.receiverExpression != this) return null
    val callExpression = dotQualifiedExpression.selectorExpression as? KtCallExpression ?: return null
    val indentHandler = callExpression.findTrimIndentHandler() ?: return null
    return indentHandler.getUntrimmedRanges(this, ElementManipulators.getValueTextRange(this))
}

private fun KtExpression?.computeConstantExpression(): Any? {
    val language = this?.language ?: return null
    val expressionEvaluator = LanguageConstantExpressionEvaluator.INSTANCE.forLanguage(language)
    return expressionEvaluator.computeConstantExpression(this, false)
}
