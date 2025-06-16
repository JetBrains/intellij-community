@file:OptIn(IntellijInternalApi::class)

package com.intellij.searchEverywhereMl.ranking.core.id

import com.intellij.execution.actions.ChooseRunConfigurationPopup
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.searchEverywhereMl.ranking.ext.SearchEverywhereElementKeyProvider

private class RunConfigurationsKeyProvider: SearchEverywhereElementKeyProvider {
  override fun getKeyOrNull(element: Any): Any? {
    if (element is ChooseRunConfigurationPopup.ItemWrapper<*>) {
      return element.value
    }
    return null
  }
}