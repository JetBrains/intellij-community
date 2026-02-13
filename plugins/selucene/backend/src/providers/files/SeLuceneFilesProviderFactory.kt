package com.intellij.selucene.backend.providers.files

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeItemsProviderFactory
import com.intellij.selucene.common.SeLuceneProviderIdUtils

class SeLuceneFilesProviderFactory : SeItemsProviderFactory {
  override val id: String
    get() = SeLuceneProviderIdUtils.LUCENE_FILES

  override suspend fun getItemsProvider(project: Project?, dataContext: DataContext): SeItemsProvider? {
    project ?: return null
    return SeLuceneFilesProvider(project)
  }
}