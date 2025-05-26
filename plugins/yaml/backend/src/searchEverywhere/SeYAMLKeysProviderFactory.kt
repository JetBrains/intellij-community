// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.searchEverywhere

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.providers.SeAsyncContributorWrapper
import com.intellij.platform.searchEverywhere.providers.SeWrappedLegacyContributorItemsProviderFactory
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeYAMLKeysProviderFactory : SeWrappedLegacyContributorItemsProviderFactory {
  override val id: String
    get() = SeYAMLKeysProviderIdUtils.YAML_ID

  override suspend fun getItemsProvider(project: Project?, legacyContributor: SearchEverywhereContributor<Any>): SeItemsProvider? {
    if (project == null) return null
    return SeYAMLKeysProvider(SeAsyncContributorWrapper(legacyContributor))
  }
}