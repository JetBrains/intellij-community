// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.selucene.backend.providers.lucene

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.launch

@Suppress("HardCodedStringLiteral")
class IndexFilesForLuceneAction: DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    val indexer = LuceneIndexer.getInstance(project)
    indexer.coroutineScope.launch {
      withBackgroundProgress(project, "Indexing files for Lucene") {
        indexFiles(project)
      }
    }
    Messages.showInfoMessage(project, "Lucene Indexing done!", "SeLucene")
  }

  private suspend fun indexFiles(project: Project) {
    LuceneIndexer.getInstance(project).indexAll()
  }
}