package com.intellij.searchEverywhereMl.ranking.id

import com.intellij.ide.ui.search.OptionDescription
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.openapi.actionSystem.AnAction

private class ActionKeyProvider: ElementKeyForIdProvider {
  override fun getKey(element: Any): Any? {
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