package com.intellij.selucene.backend.providers.files.debugActions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.selucene.backend.providers.files.FileIndex
import com.intellij.selucene.backend.providers.files.LuceneFileIndexOperation

@Suppress("HardCodedStringLiteral")
class IndexFilesForLuceneAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    val luceneIndex = FileIndex.getInstance(project)
    luceneIndex.scheduleIndexingOp(LuceneFileIndexOperation.IndexAll)
  }
}