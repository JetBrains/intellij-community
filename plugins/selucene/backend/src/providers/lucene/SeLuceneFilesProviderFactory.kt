// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.selucene.backend.providers.lucene

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