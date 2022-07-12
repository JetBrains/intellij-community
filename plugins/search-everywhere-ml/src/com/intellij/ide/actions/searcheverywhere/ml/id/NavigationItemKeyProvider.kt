package com.intellij.ide.actions.searcheverywhere.ml.id

import com.intellij.navigation.NavigationItem
import com.intellij.navigation.PsiElementNavigationItem

private class NavigationItemKeyProvider : ElementKeyForIdProvider() {
  override fun getKey(element: Any): Any? {
    return when (element) {
      is PsiElementNavigationItem -> element.targetElement
      is NavigationItem -> element.name?.let { it + element.presentation?.locationString }
      else -> null
    }
  }
}