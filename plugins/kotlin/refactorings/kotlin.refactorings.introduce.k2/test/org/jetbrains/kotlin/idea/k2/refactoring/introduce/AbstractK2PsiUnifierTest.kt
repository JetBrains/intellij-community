// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.introduce

import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.patternMatching.AbstractKotlinPsiUnifierTest

abstract class AbstractK2PsiUnifierTest : AbstractKotlinPsiUnifierTest() {
    override fun isFirPlugin(): Boolean = true

    @OptIn(KtAllowAnalysisOnEdt::class)
    override fun KtElement.getMatches(file: KtFile): List<TextRange> = allowAnalysisOnEdt {
        analyze(file) {
            val matches = K2SemanticMatcher.findMatches(patternElement = this@getMatches, scopeElement = file)
            for (match in matches) {
                assertTrue(
                    "Unexpected asymmetry in matching of ${match.text} and ${this@getMatches.text}",
                    with(K2SemanticMatcher) { this@getMatches.isSemanticMatch(match) },
                )
            }

            matches.map { it.textRange }
        }
    }
}