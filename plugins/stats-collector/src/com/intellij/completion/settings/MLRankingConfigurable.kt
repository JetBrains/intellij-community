// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.settings

import com.intellij.completion.StatsCollectorBundle
import com.intellij.internal.ml.completion.RankingModelProvider
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.*

class MLRankingConfigurable(private val availableProviders: List<RankingModelProvider>) : BoundConfigurable("ML Ranking") {
  private val settings = CompletionMLRankingSettings.getInstance()

  override fun createPanel(): DialogPanel {
    val providers = availableProviders.sortedBy { it.displayNameInSettings }
    return panel {
      var enableRankingCheckbox: CellBuilder<JBCheckBox>? = null
      titledRow(StatsCollectorBundle.message("ml.completion.settings.group")) {
        row {
          val enableRanking = checkBox(StatsCollectorBundle.message("ml.completion.enable"), settings::isRankingEnabled,
                                       { settings.isRankingEnabled = it })
          for (ranker in providers) {
            row {
              checkBox(ranker.displayNameInSettings, { settings.isLanguageEnabled(ranker.id) },
                       { settings.setLanguageEnabled(ranker.id, it) })
                .enableIf(enableRanking.selected)
            }.apply { if (ranker === providers.last()) largeGapAfter() }
          }
          enableRankingCheckbox = enableRanking
          row {
            enableRankingCheckbox?.let { enableRanking ->
              checkBox(StatsCollectorBundle.message("ml.completion.show.diff"),
                       { settings.isShowDiffEnabled },
                       { settings.isShowDiffEnabled = it }).enableIf(enableRanking.selected)
            }
          }
        }
      }
    }
  }
}
