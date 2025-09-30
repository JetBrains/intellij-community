// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.searchEverywhere

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.backend.providers.navigation.SeNavigationItem
import com.intellij.platform.searchEverywhere.providers.AsyncProcessor
import com.intellij.platform.searchEverywhere.providers.SeAsyncContributorWrapper
import com.intellij.platform.searchEverywhere.providers.SeWrappedLegacyContributorItemsProvider
import com.intellij.platform.searchEverywhere.providers.getExtendedInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import org.jetbrains.yaml.navigation.YAMLKeyNavigationItem

@Internal
class SeYAMLKeysProvider(private val contributorWrapper: SeAsyncContributorWrapper<Any>) : SeWrappedLegacyContributorItemsProvider() {
  override val id: String get() = SeYAMLKeysProviderIdUtils.YAML_ID
  override val displayName: @Nls String
    get() = contributor.fullGroupName
  override val contributor: SearchEverywhereContributor<Any> = contributorWrapper.contributor

  override suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector) {
    contributorWrapper.fetchElements(params.inputQuery, object : AsyncProcessor<Any> {
      override suspend fun process(item: Any, weight: Int): Boolean {
        if (item !is YAMLKeyNavigationItem) return true
        return collector.put(SeNavigationItem(item, contributor, weight, contributor.getExtendedInfo(item), contributorWrapper.contributor.isMultiSelectionSupported))
      }
    })
  }

  override suspend fun itemSelected(item: SeItem, modifiers: Int, searchText: String): Boolean {
    val legacyItem = (item as? SeNavigationItem)?.rawObject ?: return false
    return withContext(Dispatchers.EDT) {
      contributor.processSelectedItem(legacyItem, modifiers, searchText)
    }
  }

  override suspend fun canBeShownInFindResults(): Boolean {
    return contributor.showInFindResults()
  }

  override fun dispose() {
    Disposer.dispose(contributorWrapper)
  }
}