package com.intellij.ide.actions.searcheverywhere.ml.id

import com.intellij.ide.util.gotoByName.GotoActionModel

private class ActionKeyProvider: ElementKeyForIdProvider() {
  override fun getKey(element: Any): Any? {
    if (element is GotoActionModel.MatchedValue) {
      val elementValue = element.value

      return if (elementValue is GotoActionModel.ActionWrapper) {
        elementValue.action
      } else {
        elementValue
      }
    }

    return null
  }
}