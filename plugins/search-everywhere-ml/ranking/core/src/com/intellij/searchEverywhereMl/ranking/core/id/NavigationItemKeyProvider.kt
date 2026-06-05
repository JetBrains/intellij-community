package com.intellij.searchEverywhereMl.ranking.core.id

import com.intellij.navigation.NavigationItem
import com.intellij.navigation.PsiElementNavigationItem
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.searchEverywhereMl.ranking.ext.SearchEverywhereElementKeyProvider

internal class NavigationItemKeyProvider : SearchEverywhereElementKeyProvider {
  override fun getKeyOrNull(element: Any): Any? {
    return when (element) {
      is PsiElementNavigationItem -> runReadActionBlocking { element.targetElement }
      is NavigationItem -> runReadActionBlocking { element.name?.let { it + element.presentation?.locationString } }
      else -> null
    }
  }
}