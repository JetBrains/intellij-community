package com.intellij.searchEverywhereMl.ranking.id

import com.intellij.execution.actions.ChooseRunConfigurationPopup

private class RunConfigurationsKeyProvider: ElementKeyForIdProvider {
  override fun getKey(element: Any): Any? {
    if (element is ChooseRunConfigurationPopup.ItemWrapper<*>) {
      return element.value
    }
    return null
  }
}