@file:OptIn(IntellijInternalApi::class)

package com.intellij.searchEverywhereMl.ranking.core.id

import com.intellij.navigation.NavigationItem
import com.intellij.navigation.PsiElementNavigationItem
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.searchEverywhereMl.ranking.ext.SearchEverywhereElementKeyProvider

private class NavigationItemKeyProvider : SearchEverywhereElementKeyProvider {
  override fun getKeyOrNull(element: Any): Any? {
    return when (element) {
      is PsiElementNavigationItem -> element.targetElement
      is NavigationItem -> runReadAction { element.name?.let { it + element.presentation?.locationString } }
      else -> null
    }
  }
}