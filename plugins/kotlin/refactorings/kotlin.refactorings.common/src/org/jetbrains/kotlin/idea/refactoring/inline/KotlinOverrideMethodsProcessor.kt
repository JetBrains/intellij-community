// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.inline

import com.intellij.psi.PsiElement
import com.intellij.refactoring.OverrideMethodsProcessor
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.search.declarationsSearch.hasOverridingElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty

class KotlinOverrideMethodsProcessor : OverrideMethodsProcessor {
    override fun removeOverrideAttribute(element: PsiElement): Boolean {
        val kotlinElement = element.unwrapped ?: return false
        if (kotlinElement !is KtNamedFunction && kotlinElement !is KtProperty) return false
        kotlinElement as KtNamedDeclaration

        if (!kotlinElement.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return false

        kotlinElement.addOpenModifierIfNeeded()
        kotlinElement.removeModifier(KtTokens.OVERRIDE_KEYWORD)
        kotlinElement.removeModifier(KtTokens.FINAL_KEYWORD)

        return true
    }
}

//called under potemkin progress
@OptIn(KtAllowAnalysisOnEdt::class, KtAllowAnalysisFromWriteAction::class)
private fun KtNamedDeclaration.addOpenModifierIfNeeded() {
    if (hasModifier(KtTokens.ABSTRACT_KEYWORD) || hasModifier(KtTokens.OPEN_KEYWORD) || hasModifier(KtTokens.FINAL_KEYWORD)) return
    allowAnalysisFromWriteAction {
        allowAnalysisOnEdt {
            if (!hasOverridingElement()) return
        }
    }

    addModifier(KtTokens.OPEN_KEYWORD)
}