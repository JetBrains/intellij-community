// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.completion.settings.CompletionMLRankingSettings
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.stats.storage.factors.LookupStorage
import com.intellij.ui.JBColor
import java.awt.Color
import java.util.concurrent.atomic.AtomicInteger

class ItemsDiffCustomizingContributor : CompletionContributor() {
  companion object {
    val DIFF_KEY = Key.create<AtomicInteger>("ItemsDiffCustomizerContributor.DIFF_KEY")
  }

  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    if (shouldShowDiff(parameters)) {
      result.runRemainingContributors(parameters) {
        result.passResult(it.withLookupElement(MovedLookupElement(it.lookupElement)))
      }
    }
    else {
      super.fillCompletionVariants(parameters, result)
    }
  }

  private fun shouldShowDiff(parameters: CompletionParameters): Boolean {
    val mlRankingSettings = CompletionMLRankingSettings.getInstance()
    if (!mlRankingSettings.isShowDiffEnabled) return false
    if (!mlRankingSettings.isRankingEnabled) return false
    val lookup = LookupManager.getActiveLookup(parameters.editor) as? LookupImpl ?: return false
    return LookupStorage.get(lookup)?.model != null
  }

  private class MovedLookupElement(delegate: LookupElement) : LookupElementDecorator<LookupElement>(delegate) {
    private companion object {
      val ML_RANK_DIFF_GREEN_COLOR = JBColor(JBColor.GREEN.darker(), JBColor.GREEN.brighter())
    }

    override fun renderElement(presentation: LookupElementPresentation) {
      super.renderElement(presentation)
      val diff = getUserData(DIFF_KEY)?.get()
      if (diff == null || diff == 0) return
      val text = if (diff < 0) " ↑${-diff} " else " ↓$diff "
      val color: Color = if (diff < 0) ML_RANK_DIFF_GREEN_COLOR else JBColor.RED

      val fragments = presentation.tailFragments
      presentation.setTailText(text, color)
      for (fragment in fragments) {
        presentation.appendTailText(fragment.text, fragment.isGrayed)
      }
    }
  }

}