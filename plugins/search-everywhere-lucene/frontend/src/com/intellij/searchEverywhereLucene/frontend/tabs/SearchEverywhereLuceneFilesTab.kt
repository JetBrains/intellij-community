// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.searchEverywhereLucene.frontend.tabs

import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SePreviewInfo
import com.intellij.platform.searchEverywhere.SeResultEvent
import com.intellij.platform.searchEverywhere.frontend.SeFilterEditor
import com.intellij.platform.searchEverywhere.frontend.SeTab
import com.intellij.platform.searchEverywhere.frontend.resultsProcessing.SeTabDelegate
import com.intellij.platform.searchEverywhere.frontend.tabs.SeDefaultTabBase
import com.intellij.searchEverywhereLucene.common.SearchEverywhereLuceneProviderIdUtils
import com.intellij.searchEverywhereLucene.frontend.SearchEverywhereLuceneFrontendBundle
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.Nls

class SearchEverywhereLuceneFilesTab(delegate: SeTabDelegate) : SeDefaultTabBase(delegate) {
  override val name: @Nls String get() = SearchEverywhereLuceneFrontendBundle.message("searchEverywhereLucene.files.tab.name")
  override val id: String get() = SearchEverywhereLuceneProviderIdUtils.LUCENE_FILES
  override val priority: Int
    get() = 10

  override suspend fun getFilterEditor(): SeFilterEditor? = null
  override suspend fun canBeShownInFindResults(): Boolean = false
  override suspend fun performExtendedAction(item: SeItemData): Boolean = false
  override suspend fun isCommandsSupported(): Boolean = false

  override fun dispose() {}
}