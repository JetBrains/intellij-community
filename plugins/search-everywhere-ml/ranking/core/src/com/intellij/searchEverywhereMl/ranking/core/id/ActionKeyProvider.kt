@file:OptIn(IntellijInternalApi::class)

package com.intellij.searchEverywhereMl.ranking.core.id

import com.intellij.ide.ui.search.OptionDescription
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.searchEverywhereMl.ranking.ext.SearchEverywhereElementKeyProvider

private class ActionKeyProvider: SearchEverywhereElementKeyProvider {
  override fun getKeyOrNull(element: Any): Any? {
    if (element is GotoActionModel.MatchedValue) {
      val elementValue = element.value

      return if (elementValue is GotoActionModel.ActionWrapper) {
        elementValue.action
      } else {
        elementValue
      }
    }

    if (element is GotoActionModel.ActionWrapper) {
      return element.action
    }
    else if (element is OptionDescription || element is AnAction) {
      return element
    }
    return null
  }
}