// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.spellchecker.DictionaryLayer
import com.intellij.spellchecker.statistics.SpellcheckerRateTracker
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Extension point for product-wide spellchecking inspection quickfixes customization.
 */
@Internal
abstract class SpellCheckerQuickFixFactory {
  companion object {
    private val EP_NAME = ExtensionPointName.create<SpellCheckerQuickFixFactory>("com.intellij.spellchecker.quickFixFactory")

    @JvmStatic
    @JvmOverloads
    fun rename(typo: String, range: TextRange, element: PsiElement, tracker: SpellcheckerRateTracker? = null): LocalQuickFix {
      return EP_NAME.extensionList.firstNotNullOfOrNull { it.createRename(element) } ?: RenameTo(typo, range, element, tracker)
    }

    @JvmStatic
    @JvmOverloads
    fun changeToVariants(element: PsiElement, rangeInElement: TextRange, word: String, tracker: SpellcheckerRateTracker? = null): List<LocalQuickFix> {
      return EP_NAME.extensionList.firstNotNullOfOrNull { it.createChangeToVariantsFixes(element, rangeInElement, word) }
             ?: ChangeTo(word, element, rangeInElement, tracker).getAllAsFixes()
    }

    @JvmStatic
    @JvmOverloads
    fun saveTo(element: PsiElement, rangeInElement: TextRange, word: String, tracker: SpellcheckerRateTracker? = null): LocalQuickFix {
      return saveTo(element, rangeInElement, word, null, tracker)
    }

    @JvmStatic
    @JvmOverloads
    fun saveTo(element: PsiElement, rangeInElement: TextRange, word: String, layer: DictionaryLayer?, tracker: SpellcheckerRateTracker? = null): LocalQuickFix {
      return EP_NAME.extensionList.firstNotNullOfOrNull { it.createSaveToFix(element, rangeInElement, word, layer) }
             ?: SaveTo(word, layer, tracker)
    }
  }

  open fun createRename(element: PsiElement): LocalQuickFix? = null
  open fun createChangeToVariantsFixes(element: PsiElement, rangeInElement: TextRange, word: String): List<LocalQuickFix>? = null
  open fun createSaveToFix(element: PsiElement, rangeInElement: TextRange, word: String, layer: DictionaryLayer?): LocalQuickFix? = null
}