@file:OptIn(IntellijInternalApi::class)

package com.intellij.searchEverywhereMl.ranking.vcs

import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.searchEverywhereMl.ranking.ext.SearchEverywhereElementKeyProvider
import com.intellij.vcs.log.impl.TimedVcsCommitImpl
import com.intellij.vcs.log.impl.VcsRefImpl

internal class SearchEverywhereVcsElementKeyProvider: SearchEverywhereElementKeyProvider {
  override fun getKeyOrNull(element: Any) = when (element) {
    is VcsRefImpl ->  element
    is TimedVcsCommitImpl -> element
    else -> null
  }
}