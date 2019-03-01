// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.AbstractVcsHelper
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import org.jetbrains.idea.svn.SvnBundle
import org.jetbrains.idea.svn.SvnLocallyDeletedChange
import org.jetbrains.idea.svn.SvnVcs
import org.jetbrains.idea.svn.api.Depth

private fun getConflictedChange(e: AnActionEvent) =
  e.getData(ChangesListView.LOCALLY_DELETED_CHANGES)?.singleOrNull() as? SvnLocallyDeletedChange

class MarkLocallyDeletedTreeConflictResolvedAction : DumbAwareAction(SvnBundle.message("action.Subversion.MarkTreeResolved.text")) {
  override fun update(e: AnActionEvent) {
    val project = e.project
    val conflictedChange = getConflictedChange(e)
    val enabled = project != null && conflictedChange?.conflictState?.isTree == true

    e.presentation.isEnabledAndVisible = enabled
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val conflictedChanged = getConflictedChange(e)!!

    val markText = SvnBundle.message("action.mark.tree.conflict.resolved.confirmation.title")
    val result = Messages.showYesNoDialog(project, SvnBundle.message("action.mark.tree.conflict.resolved.confirmation.text"), markText,
                                          Messages.getQuestionIcon())
    if (result == Messages.YES) {
      object : Task.Backgroundable(project, markText, true) {
        private var exception: VcsException? = null

        override fun run(indicator: ProgressIndicator) {
          val path = conflictedChanged.path
          val vcs = SvnVcs.getInstance(project)

          try {
            vcs.getFactory(path.ioFile).createConflictClient().resolve(path.ioFile, Depth.EMPTY, false, false, true)
          }
          catch (e: VcsException) {
            exception = e
          }

          VcsDirtyScopeManager.getInstance(project).filePathsDirty(listOf(path), null)
        }

        override fun onSuccess() {
          if (exception != null) {
            AbstractVcsHelper.getInstance(project).showError(exception, markText)
          }
        }
      }.queue()
    }
  }
}
