package com.intellij.ide.actions.searcheverywhere.ml.id

import com.intellij.navigation.NavigationItem

private class NavigationItemKeyProvider : ElementKeyForIdProvider() {
  override fun getKey(element: Any): Any? {
    return element as? NavigationItem
  }
}