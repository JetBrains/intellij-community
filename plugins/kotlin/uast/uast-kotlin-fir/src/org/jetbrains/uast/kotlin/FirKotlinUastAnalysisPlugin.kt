// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.kotlin

import com.intellij.lang.Language
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.analysis.UExpressionFact
import org.jetbrains.uast.analysis.UNullability
import org.jetbrains.uast.analysis.UastAnalysisPlugin
import org.jetbrains.uast.kotlin.internal.analyzeForUast

class FirKotlinUastAnalysisPlugin : UastAnalysisPlugin {
    override val language: Language get() = KotlinLanguage.INSTANCE

    override fun <T : Any> UExpression.getExpressionFact(fact: UExpressionFact<T>): T? {
        val psiExpression = (sourcePsi as? KtExpression)?.unwrapBlockOrParenthesis() ?: return null
        @Suppress("UNCHECKED_CAST")
        return when (fact) {
            UExpressionFact.UNullabilityFact -> {
                analyzeForUast(psiExpression) {
                    when {
                        psiExpression.isDefinitelyNotNull() -> UNullability.NOT_NULL
                        psiExpression.isDefinitelyNull() -> UNullability.NULL
                        psiExpression.getKtType()?.isMarkedNullable == true -> UNullability.NULLABLE
                        else -> UNullability.UNKNOWN
                    }
                }
            }
        } as T
    }
}