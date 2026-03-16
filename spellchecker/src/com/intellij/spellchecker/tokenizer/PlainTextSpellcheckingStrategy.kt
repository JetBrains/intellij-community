// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.tokenizer

import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.spellchecker.inspections.SpellCheckingInspection.SpellCheckingScope

class PlainTextSpellcheckingStrategy: SpellcheckingStrategy() {
  override fun getTokenizer(element: PsiElement, scope: Set<SpellCheckingScope>): Tokenizer<*> {
    if (element.containingFile.name.endsWith(".sha1")) return EMPTY_TOKENIZER
    if (element.containingFile.name.endsWith(".txt") && useTextLevelSpellchecking(element)) return EMPTY_TOKENIZER
    return super.getTokenizer(element, scope)
  }

  override fun useTextLevelSpellchecking(): Boolean {
    return Registry.`is`("spellchecker.grazie.enabled", false)
  }
}