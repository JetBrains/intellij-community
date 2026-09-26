// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.searchEverywhereLucene.frontend.tabs

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.SeSession
import com.intellij.platform.searchEverywhere.frontend.SeEssentialTabFactory
import com.intellij.platform.searchEverywhere.frontend.SeTab
import com.intellij.platform.searchEverywhere.frontend.SeTabFactory
import com.intellij.platform.searchEverywhere.frontend.resultsProcessing.SeTabDelegate
import com.intellij.searchEverywhereLucene.common.SearchEverywhereLuceneProviderIdUtils
import kotlinx.coroutines.CoroutineScope

class SearchEverywhereLuceneFilesTabFactory : SeTabFactory, SeEssentialTabFactory {
  override val id: String get() = SearchEverywhereLuceneProviderIdUtils.LUCENE_FILES
  override val name: String get() = SearchEverywhereLuceneFilesTab.NAME
  override val priority: Int get() = SearchEverywhereLuceneFilesTab.PRIORITY

  override suspend fun getTab(
    scope: CoroutineScope,
    project: Project?,
    session: SeSession,
    initEvent: AnActionEvent,
    registerShortcut: (AnAction) -> Unit,
  ): SeTab? {
    if (project == null) return null
    val delegate = SeTabDelegate.create(project,
                                        session,
                                        "LuceneFiles",
                                        listOf(SeProviderId(SearchEverywhereLuceneProviderIdUtils.LUCENE_FILES)),
                                        initEvent,
                                        scope)

    return SearchEverywhereLuceneFilesTab(delegate)
  }
}