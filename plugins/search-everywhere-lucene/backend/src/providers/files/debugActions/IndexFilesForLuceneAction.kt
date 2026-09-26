package com.intellij.searchEverywhereLucene.backend.providers.files.debugActions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.searchEverywhereLucene.backend.providers.files.FileIndex
import com.intellij.searchEverywhereLucene.backend.providers.files.LuceneFileIndexOperation

class IndexFilesForLuceneAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    val luceneIndex = FileIndex.getInstanceIfEnabled(project) ?: return
    luceneIndex.scheduleIndexingOp(LuceneFileIndexOperation.IndexAll)
  }
}
