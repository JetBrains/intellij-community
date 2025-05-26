// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.searchEverywhere

import com.intellij.ide.util.DelegatingProgressIndicator
import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.backend.providers.navigation.SeNavigationItem
import com.intellij.platform.searchEverywhere.providers.SeAsyncContributorWrapper
import com.intellij.platform.searchEverywhere.providers.getExtendedDescription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import org.jetbrains.yaml.navigation.YAMLKeyNavigationItem

@Internal
class SeYAMLKeysProvider(private val contributorWrapper: SeAsyncContributorWrapper<Any>) : SeItemsProvider {
  override val id: String get() = SeYAMLKeysProviderIdUtils.YAML_ID
  override val displayName: @Nls String
    get() = contributorWrapper.contributor.fullGroupName

  override suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector) {
    coroutineToIndicator {
      val indicator = DelegatingProgressIndicator(ProgressManager.getGlobalProgressIndicator())
      contributorWrapper.contributor.fetchElements(params.inputQuery, indicator) { item ->
        (item as? YAMLKeyNavigationItem)?.let {
          val weight = contributorWrapper.contributor.getElementPriority(item, params.inputQuery)
          runBlockingCancellable { collector.put(SeNavigationItem(it, weight, getExtendedDescription(it))) }
        } ?: true
      }
    }
  }

  override suspend fun itemSelected(item: SeItem, modifiers: Int, searchText: String): Boolean {
    val legacyItem = (item as? SeNavigationItem)?.item ?: return false
    return withContext(Dispatchers.EDT) {
      contributorWrapper.contributor.processSelectedItem(legacyItem, modifiers, searchText)
    }
  }

  override suspend fun canBeShownInFindResults(): Boolean {
    return contributorWrapper.contributor.showInFindResults()
  }

  fun getExtendedDescription(item: Any): String? {
    return contributorWrapper.contributor.getExtendedDescription(item)
  }

  override fun dispose() {
    Disposer.dispose(contributorWrapper)
  }
}