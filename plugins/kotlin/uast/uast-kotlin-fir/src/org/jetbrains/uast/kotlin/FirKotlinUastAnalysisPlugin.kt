// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.kotlin

import com.intellij.lang.Language
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.analysis.UExpressionFact
import org.jetbrains.uast.analysis.UNullability
import org.jetbrains.uast.analysis.UastAnalysisPlugin
import org.jetbrains.uast.kotlin.internal.analyzeForUast

class FirKotlinUastAnalysisPlugin : UastAnalysisPlugin {
    override val language: Language get() = KotlinLanguage.INSTANCE

    override fun <T : Any> UExpression.getExpressionFact(fact: UExpressionFact<T>): T? {
        val ktElement = (sourcePsi as? KtElement) ?: return null
        @Suppress("UNCHECKED_CAST")
        return when (fact) {
            UExpressionFact.UNullabilityFact -> checkNullability(ktElement)
        } as T
    }

    private fun checkNullability(ktElement: KtElement): UNullability? {
        return analyzeForUast(ktElement) {
            when (ktElement) {
                is KtExpression -> checkNullabilityForExpression(ktElement)
                is KtTypeReference -> checkNullabilityForType(ktElement.type)
                else -> null
            }
        }
    }

    private fun KaSession.checkNullabilityForType(kaType: KaType): UNullability? {
        return when (kaType.nullability) {
            KaTypeNullability.NULLABLE -> UNullability.NULLABLE
            KaTypeNullability.NON_NULLABLE -> UNullability.NOT_NULL
            KaTypeNullability.UNKNOWN -> UNullability.UNKNOWN
        }
    }

    private fun KaSession.checkNullabilityForExpression(expression: KtExpression): UNullability? {
        val unwrappedExpression = expression.unwrapBlockOrParenthesis()

        return when {
            unwrappedExpression.isDefinitelyNotNull -> UNullability.NOT_NULL
            unwrappedExpression.isDefinitelyNull -> UNullability.NULL
            else -> unwrappedExpression.expressionType?.let { checkNullabilityForType(it) }
        }
    }
}