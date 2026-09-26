package com.intellij.searchEverywhereLucene.backend.providers.files

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeItemsProviderFactory
import com.intellij.searchEverywhereLucene.common.SearchEverywhereLuceneProviderIdUtils

class SearchEverywhereLuceneFilesProviderFactory : SeItemsProviderFactory {
  override val id: String
    get() = SearchEverywhereLuceneProviderIdUtils.LUCENE_FILES

  override suspend fun getItemsProvider(project: Project?, dataContext: DataContext): SeItemsProvider? {
    project ?: return null
    return SearchEverywhereLuceneFilesProvider(project)
  }
}