// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.spellchecker

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.spellchecker.inspections.PlainTextSplitter
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy
import com.intellij.spellchecker.tokenizer.Tokenizer
import com.intellij.spellchecker.tokenizer.TokenizerBase
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

class KotlinSpellcheckingStrategy : SpellcheckingStrategy() {
    private val plainTextTokenizer = TokenizerBase<KtLiteralStringTemplateEntry>(PlainTextSplitter.getInstance())
    private val emptyTokenizer = EMPTY_TOKENIZER

    override fun getTokenizer(element: PsiElement?): Tokenizer<out PsiElement?> {
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
            is KtLiteralStringTemplateEntry -> plainTextTokenizer
            else -> emptyTokenizer
        }
    }
}
