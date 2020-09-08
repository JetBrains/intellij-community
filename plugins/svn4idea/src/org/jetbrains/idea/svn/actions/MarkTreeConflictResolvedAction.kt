// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.AbstractVcsHelper
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.ChangesUtil.getAfterPath
import com.intellij.openapi.vcs.changes.ChangesUtil.getBeforePath
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import org.jetbrains.idea.svn.ConflictState
import org.jetbrains.idea.svn.ConflictedSvnChange
import org.jetbrains.idea.svn.SvnBundle.message
import org.jetbrains.idea.svn.SvnLocallyDeletedChange
import org.jetbrains.idea.svn.SvnVcs
import org.jetbrains.idea.svn.api.Depth

private fun getConflict(e: AnActionEvent): Conflict? {
  val changes = e.getData(VcsDataKeys.CHANGE_LEAD_SELECTION)
  val locallyDeletedChanges = e.getData(ChangesListView.LOCALLY_DELETED_CHANGES)

  if (locallyDeletedChanges.isNullOrEmpty()) {
    return (changes?.singleOrNull() as? ConflictedSvnChange)?.let { ChangeConflict(it) }
  }
  if (changes.isNullOrEmpty()) {
    return (locallyDeletedChanges.singleOrNull() as? SvnLocallyDeletedChange)?.let { LocallyDeletedChangeConflict(it) }
  }
  return null
}

class MarkTreeConflictResolvedAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val project = e.project
    val conflict = getConflict(e)
    val enabled = project != null && conflict?.conflictState?.isTree == true

    e.presentation.isEnabledAndVisible = enabled
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val conflict = getConflict(e)!!

    val result = Messages.showYesNoDialog(
      project,
      message("dialog.message.mark.tree.conflict.resolved.confirmation"),
      message("dialog.title.mark.tree.conflict.resolved"),
      Messages.getQuestionIcon()
    )
    if (result == Messages.YES) {
      object : Task.Backgroundable(project, message("progress.title.mark.tree.conflict.resolved"), true) {
        private var exception: VcsException? = null

        override fun run(indicator: ProgressIndicator) {
          val path = conflict.path
          val vcs = SvnVcs.getInstance(project)

          try {
            vcs.getFactory(path.ioFile).createConflictClient().resolve(path.ioFile, Depth.EMPTY, false, false, true)
          }
          catch (e: VcsException) {
            exception = e
          }

          VcsDirtyScopeManager.getInstance(project).filePathsDirty(conflict.getPathsToRefresh(), null)
        }

        override fun onSuccess() {
          if (exception != null) {
            AbstractVcsHelper.getInstance(project).showError(exception, message("dialog.title.mark.tree.conflict.resolved"))
          }
        }
      }.queue()
    }
  }
}

private interface Conflict {
  val path: FilePath
  val conflictState: ConflictState

  fun getPathsToRefresh(): Collection<FilePath>
}

private class ChangeConflict(val change: ConflictedSvnChange) : Conflict {
  override val path: FilePath get() = change.treeConflictMarkHolder
  override val conflictState: ConflictState get() = change.conflictState

  override fun getPathsToRefresh(): Collection<FilePath> {
    val beforePath = getBeforePath(change)
    val afterPath = getAfterPath(change)
    val isAddMoveRename = beforePath == null || change.isMoved || change.isRenamed

    return listOfNotNull(beforePath, if (isAddMoveRename) afterPath else null)
  }
}

private class LocallyDeletedChangeConflict(val change: SvnLocallyDeletedChange) : Conflict {
  override val path: FilePath get() = change.path
  override val conflictState: ConflictState get() = change.conflictState

  override fun getPathsToRefresh(): Collection<FilePath> = listOf(path)
}