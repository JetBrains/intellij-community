// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.quickfixes

import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.codeStyle.SuggestedNameInfo
import com.intellij.refactoring.rename.NameSuggestionProvider
import com.intellij.refactoring.rename.RenameUtil
import com.intellij.spellchecker.grazie.GrazieSpellCheckerEngine

class SpellcheckingNameSuggestionProvider : NameSuggestionProvider {

  private val suggestionLimit = 3

  override fun getSuggestedNames(element: PsiElement, context: PsiElement?, result: MutableSet<String>): SuggestedNameInfo? {
    val name = getName(element) ?: return null

    val engine = element.project.service<GrazieSpellCheckerEngine>()
    val speller = engine.getSpeller() ?: return null

    val suggestions = speller.suggestAndRank(name, suggestionLimit)
      .toList()
      .sortedByDescending { it.second }
      .map { it.first }
      .filter { RenameUtil.isValidName(element.project, element, it) }
      .toList()
    if (suggestions.isEmpty()) return null

    result.addAll(suggestions)
    return object : SuggestedNameInfo(suggestions.toTypedArray()) {
      override fun nameChosen(name: String) {}
    }
  }

  private fun getName(element: PsiElement): String? = if (element is PsiNamedElement) element.name else null
}