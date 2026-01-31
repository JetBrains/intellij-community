// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.spellchecker

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.spellchecker.inspections.PlainTextSplitter
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy
import com.intellij.spellchecker.tokenizer.Tokenizer
import com.intellij.spellchecker.tokenizer.TokenizerBase
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtParameterList

internal class KotlinSpellcheckingStrategy : SpellcheckingStrategy(), DumbAware {
    private val plainTextTokenizer: Tokenizer<PsiElement> = TokenizerBase(PlainTextSplitter.getInstance())
    private val emptyTokenizer: Tokenizer<PsiElement> = EMPTY_TOKENIZER

    override fun getTokenizer(element: PsiElement): Tokenizer<out PsiElement?> {
        if (useTextLevelSpellchecking() && (element is PsiComment || element is KtLiteralStringTemplateEntry)) {
            // use [KotlinTextExtractor] and [GrazieTextLevelSpellCheckingExtension] if enabled for non-code constructs
            return emptyTokenizer
        }
        return when (element) {
            is PsiComment -> super.getTokenizer(element)
            is KtParameter -> {
                val function = (element.parent as? KtParameterList)?.parent as? KtNamedFunction
                when {
                    function?.hasModifier(KtTokens.OVERRIDE_KEYWORD) == true -> emptyTokenizer
                    else -> super.getTokenizer(element)
                }
            }

            is PsiNameIdentifierOwner -> {
                when {
                    element is KtModifierListOwner && element.hasModifier(KtTokens.OVERRIDE_KEYWORD) -> emptyTokenizer
                    else -> super.getTokenizer(element)
                }
            }

            is KtLiteralStringTemplateEntry if !isInjectedLanguageFragment(element.parent) -> plainTextTokenizer

            else -> emptyTokenizer
        }
    }

    override fun useTextLevelSpellchecking(): Boolean {
        return Registry.`is`("spellchecker.grazie.enabled", false)
    }
}
