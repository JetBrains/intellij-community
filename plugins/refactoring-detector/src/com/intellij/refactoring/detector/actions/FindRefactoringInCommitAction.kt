// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.detector.actions

import com.intellij.CommonBundle
import com.intellij.json.JsonFileType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages.showDialog
import com.intellij.openapi.ui.Messages.showInfoMessage
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.refactoring.detector.RefactoringDetectorBundle.Companion.message
import com.intellij.testFramework.LightVirtualFile
import com.intellij.vcs.log.VcsLogDataKeys
import org.jetbrains.research.kotlinrminer.ide.KotlinRMiner
import org.jetbrains.research.kotlinrminer.ide.Refactoring

class FindRefactoringInCommitAction : DumbAwareAction() {

  override fun update(e: AnActionEvent) {
    val log = e.getData(VcsLogDataKeys.VCS_LOG)

    e.presentation.isEnabledAndVisible = e.project != null && log != null && log.selectedCommits.isNotEmpty()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val changes = e.getData(VcsDataKeys.CHANGES)?.toList() ?: return

    runBackgroundableTask(message("progress.find.refactoring.title"), project, false) {
      val refactorings = runReadAction { KotlinRMiner.detectRefactorings(project, changes) }
      runInEdt {
        if (refactorings.isEmpty()) {
          showInfoMessage(project, message("message.no.refactorings.found"), message("message.find.refactoring.dialog.title"))
        }
        else {
          val message = message("message.found.refactorings", refactorings.size, refactorings.joinToString(separator = "\n"))
          val title = message("message.find.refactoring.dialog.title")
          if (showDialog(project, message, title, arrayOf(message("button.show"), CommonBundle.getCancelButtonText()), 0, null) == 0) {
            showRefactoringsInEditor(project, refactorings)
          }
        }
      }
    }
  }

  private fun List<Refactoring>.toJsonRepresentation(): String =
    """[${joinToString(separator = ",\n", transform = Refactoring::toJSON)}]"""

  private fun showRefactoringsInEditor(project: Project, refactorings: List<Refactoring>) {
    val jsonFile = InMemoryJsonVirtualFile(refactorings.toJsonRepresentation())
    OpenFileDescriptor(project, jsonFile, 0).navigate(true)
  }

  private class InMemoryJsonVirtualFile(private val content: String) : LightVirtualFile(message("refactorings.file.name")) {
    override fun getContent(): CharSequence = content
    override fun getPath(): String = name
    override fun getFileType(): FileType = JsonFileType.INSTANCE
  }
}
