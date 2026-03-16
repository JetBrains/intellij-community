// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.spellchecker

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.spellchecker.inspections.PlainTextSplitter
import com.intellij.spellchecker.inspections.Splitter
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy
import com.intellij.spellchecker.tokenizer.TokenConsumer
import com.intellij.spellchecker.tokenizer.Tokenizer
import com.intellij.spellchecker.tokenizer.TokenizerBase
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtParameterList

internal class KotlinSpellcheckingStrategy : SpellcheckingStrategy(), DumbAware {
    private val plainTextTokenizer: Tokenizer<PsiElement> = TokenizerBase(PlainTextSplitter.getInstance())
    private val codeTokenizer: Tokenizer<PsiElement> = object : TokenizerBase<PsiElement>(PlainTextSplitter.getInstance()) {
        override fun consumeToken(element: PsiElement, consumer: TokenConsumer, splitter: Splitter) {
            consumer.consumeToken(element, true, splitter)
        }
    }
    private val emptyTokenizer: Tokenizer<PsiElement> = EMPTY_TOKENIZER

    override fun getTokenizer(element: PsiElement): Tokenizer<out PsiElement?> {
        if (useTextLevelSpellchecking() && (element is PsiComment || element is KtLiteralStringTemplateEntry)) {
            // use [KotlinTextExtractor] and [SpellingTextChecker] if enabled for non-code constructs
            return emptyTokenizer
        }
        return when (element) {
            is PsiComment -> super.getTokenizer(element)
            is KtLiteralStringTemplateEntry if !isInjectedLanguageFragment(element.parent) -> plainTextTokenizer
            is LeafPsiElement -> {
                if (element.elementType != KtTokens.IDENTIFIER) return emptyTokenizer
                val parent = element.parent
                if (parent is KtNameReferenceExpression) return emptyTokenizer
                if (parent is KtParameter) {
                    val function = (parent.parent as? KtParameterList)?.parent as? KtNamedFunction
                    if (function is KtNamedFunction) return getTokenizer(function)
                }
                if (parent is KtModifierListOwner) return getTokenizer(parent)
                codeTokenizer
            }
            else -> emptyTokenizer
        }
    }

    private fun getTokenizer(parent: KtModifierListOwner): Tokenizer<PsiElement> = when {
        parent.hasModifier(KtTokens.OVERRIDE_KEYWORD) -> emptyTokenizer
        else -> codeTokenizer
    }

    override fun useTextLevelSpellchecking(): Boolean = Registry.`is`("spellchecker.grazie.enabled", false)
}
