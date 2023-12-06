package com.intellij.searchEverywhereMl.ranking.id

import com.intellij.navigation.NavigationItem
import com.intellij.navigation.PsiElementNavigationItem
import com.intellij.openapi.application.runReadAction

private class NavigationItemKeyProvider : ElementKeyForIdProvider {
  override fun getKey(element: Any): Any? {
    return when (element) {
      is PsiElementNavigationItem -> element.targetElement
      is NavigationItem -> runReadAction { element.name?.let { it + element.presentation?.locationString } }
      else -> null
    }
  }
}