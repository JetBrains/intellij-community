package com.intellij.searchEverywhereMl.semantics.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.searchEverywhereMl.semantics.SemanticSearchBundle
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel

class SemanticSearchConfigurable : BoundConfigurable(
  SemanticSearchBundle.getMessage("search.everywhere.ml.semantic.settings.configurable.display.name")
) {
  private val settings = SemanticSearchSettingsManager.getInstance()

  override fun createPanel(): DialogPanel {
    return panel {
      row {
        checkBox(
          SemanticSearchBundle.getMessage("search.everywhere.ml.semantic.settings.configurable.actions.enable")
        ).bindSelected(settings::getIsEnabledInActionsTab, settings::setIsEnabledInActionsTab)
      }
    }
  }
}