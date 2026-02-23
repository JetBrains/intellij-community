// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.selucene.frontend.tabs

import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SePreviewInfo
import com.intellij.platform.searchEverywhere.SeResultEvent
import com.intellij.platform.searchEverywhere.frontend.SeFilterEditor
import com.intellij.platform.searchEverywhere.frontend.SeTab
import com.intellij.platform.searchEverywhere.frontend.resultsProcessing.SeTabDelegate
import com.intellij.selucene.common.SeLuceneProviderIdUtils
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.Nls

@Suppress("HardCodedStringLiteral")
class SeLuceneFilesTab(private val delegate: SeTabDelegate) : SeTab {
  override val name: @Nls String get() = "Lucene Files"
  override val id: String get() = SeLuceneProviderIdUtils.LUCENE_FILES
  override val priority: Int
    get() = 10

  override fun getItems(params: SeParams): Flow<SeResultEvent> = delegate.getItems(params)
  override suspend fun getFilterEditor(): SeFilterEditor? = null
  override suspend fun itemSelected(item: SeItemData, modifiers: Int, searchText: String): Boolean =
    delegate.itemSelected(item, modifiers, searchText)

  override suspend fun canBeShownInFindResults(): Boolean = false
  override suspend fun performExtendedAction(item: SeItemData): Boolean = false
  override suspend fun isPreviewEnabled(): Boolean = delegate.isPreviewEnabled()
  override suspend fun getPreviewInfo(itemData: SeItemData): SePreviewInfo? = delegate.getPreviewInfo(itemData, false)
  override suspend fun isExtendedInfoEnabled(): Boolean = delegate.isExtendedInfoEnabled()

  override suspend fun isCommandsSupported(): Boolean = false

  override fun dispose() {
    Disposer.dispose(delegate)
  }


}